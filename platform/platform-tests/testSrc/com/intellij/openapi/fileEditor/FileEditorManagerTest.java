// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsState;
import com.intellij.mock.Mock;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileEditor.impl.*;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.Assume;

import javax.swing.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class FileEditorManagerTest extends FileEditorManagerTestCase {
  public void testTabOrder() {
    openFiles(STRING.replace("pinned=\"true\"", "pinned=\"false\""));
    assertOpenFiles("1.txt", "foo.xml", "2.txt", "3.txt");

    manager.closeAllFiles();
    openFiles(STRING);
    assertOpenFiles("foo.xml", "1.txt", "2.txt", "3.txt");
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      UISettingsState template = new UISettingsState();
      UISettingsState uiSettings = UISettings.getInstance().getState();
      uiSettings.setEditorTabLimit(template.getEditorTabLimit());
      uiSettings.setReuseNotModifiedTabs(template.getReuseNotModifiedTabs());
      uiSettings.setEditorTabPlacement(template.getEditorTabPlacement());
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testTabLimit() throws Exception {
    UISettings.getInstance().getState().setEditorTabLimit(2);
    openFiles(STRING);
    // note that foo.xml is pinned
    assertOpenFiles("foo.xml", "3.txt");
  }

  public void testTabLimitWithJupyterNotebooks() {
    manager.openFile(getFile("/src/test.ipynb"), true);
    manager.closeAllFiles();
    manager.openFile(getFile("/src/1.txt"), true);
    UISettings.getInstance().getState().setEditorTabLimit(1);
    manager.openFile(getFile("/src/test.ipynb"), true);
    assertOpenFiles("test.ipynb");
  }

  public void testSingleTabLimit() throws Exception {
    UISettings.getInstance().getState().setEditorTabLimit(1);
    openFiles(STRING.replace("pinned=\"true\"", "pinned=\"false\""));
    assertOpenFiles("3.txt");

    manager.closeAllFiles();

    openFiles(STRING);
    // note that foo.xml is pinned
    assertOpenFiles("foo.xml");
    manager.openFile(getFile("/src/3.txt"), true);
    assertOpenFiles("foo.xml", "3.txt");//limit is still 1 but pinned prevent closing tab and actual tab number may exceed the limit

    manager.closeAllFiles();

    manager.openFile(getFile("/src/3.txt"), true);
    manager.openFile(getFile("/src/foo.xml"), true);
    assertOpenFiles("foo.xml");
    callTrimToSize();
    assertOpenFiles("foo.xml");
  }

  public void testReuseNotModifiedTabs() {
    UISettingsState uiSettings = UISettings.getInstance().getState();
    uiSettings.setEditorTabLimit(2);
    uiSettings.setReuseNotModifiedTabs(false);

    manager.openFile(getFile("/src/3.txt"), true);
    manager.openFile(getFile("/src/foo.xml"), true);
    assertOpenFiles("3.txt", "foo.xml");
    uiSettings.setEditorTabLimit(1);
    callTrimToSize();
    assertOpenFiles("foo.xml");
    uiSettings.setEditorTabLimit(2);

    manager.closeAllFiles();

    uiSettings.setReuseNotModifiedTabs(true);
    manager.openFile(getFile("/src/3.txt"), true);
    assertOpenFiles("3.txt");
    manager.openFile(getFile("/src/foo.xml"), true);
    assertOpenFiles("foo.xml");
  }

  private void callTrimToSize() {
    for (EditorsSplitters each : manager.getAllSplitters()) {
      each.trimToSize();
    }
  }

  public void testOpenRecentEditorTab() throws Exception {
    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new MyFileEditorProvider(), myFixture.getTestRootDisposable());

    openFiles("""
                  <component name="FileEditorManager">
                    <leaf>
                      <file pinned="false" current="true" current-in-tab="true">
                        <entry selected="true" file="file://$PROJECT_DIR$/src/1.txt">
                          <provider editor-type-id="mock" selected="true">
                            <state />
                          </provider>
                          <provider editor-type-id="text-editor">
                            <state/>
                          </provider>
                        </entry>
                      </file>
                    </leaf>
                  </component>
                """);
    FileEditor[] selectedEditors = manager.getSelectedEditors();
    assertEquals(1, selectedEditors.length);
    assertEquals(MyFileEditorProvider.DEFAULT_FILE_EDITOR_NAME, selectedEditors[0].getName());
  }

  public void testTrackSelectedEditor() {
    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new MyFileEditorProvider(), myFixture.getTestRootDisposable());
    VirtualFile file = getFile("/src/1.txt");
    assertNotNull(file);
    FileEditor[] editors = manager.openFile(file, true);
    assertEquals(2, editors.length);
    assertEquals("Text", manager.getSelectedEditor(file).getName());
    manager.setSelectedEditor(file, "mock");
    assertEquals(MyFileEditorProvider.DEFAULT_FILE_EDITOR_NAME, manager.getSelectedEditor(file).getName());

    VirtualFile file1 = getFile("/src/2.txt");
    manager.openFile(file1, true);
    assertEquals(MyFileEditorProvider.DEFAULT_FILE_EDITOR_NAME, manager.getSelectedEditor(file).getName());
  }

  public void testWindowClosingRetainsOtherWindows() {
    VirtualFile file = getFile("/src/1.txt");
    assertNotNull(file);
    manager.openFile(file, false);
    EditorWindow primaryWindow = manager.getCurrentWindow();
    assertNotNull(primaryWindow);
    manager.createSplitter(SwingConstants.VERTICAL, primaryWindow);
    EditorWindow secondaryWindow = manager.getNextWindow(primaryWindow);
    assertNotNull(secondaryWindow);
    manager.createSplitter(SwingConstants.VERTICAL, secondaryWindow);
    manager.closeFile(file, primaryWindow);
    assertEquals(2, manager.getWindows().length);
  }

  public void testOpenFileInTablessSplitter() {
    VirtualFile file1 = getFile("/src/1.txt");
    assertNotNull(file1);
    manager.openFile(file1, false);
    VirtualFile file2 = getFile("/src/2.txt");
    assertNotNull(file2);
    manager.openFile(file2, true);
    EditorWindow primaryWindow = manager.getCurrentWindow();//1.txt and selected 2.txt
    assertNotNull(primaryWindow);
    manager.createSplitter(SwingConstants.VERTICAL, primaryWindow);
    EditorWindow secondaryWindow = manager.getNextWindow(primaryWindow);//2.txt only, selected and focused
    assertNotNull(secondaryWindow);
    UISettings.getInstance().setEditorTabPlacement(UISettings.TABS_NONE);
    manager.openFileWithProviders(file1, true, true);//Here we have to ignore 'searchForSplitter'
    assertEquals(2, primaryWindow.getTabCount());
    assertEquals(2, secondaryWindow.getTabCount());
    assertOrderedEquals(primaryWindow.getFiles(), file1, file2);
    assertOrderedEquals(secondaryWindow.getFiles(), file2, file1);
  }

  public void testStoringCaretStateForFileWithFoldingsWithNoTabs() {
    UISettings.getInstance().setEditorTabPlacement(UISettings.TABS_NONE);
    VirtualFile file = getFile("/src/Test.java");
    assertNotNull(file);
    Assume.assumeTrue("JAVA".equals(file.getFileType().getName())); // otherwise, the folding'd be incorrect
    FileEditor[] editors = manager.openFile(file, false);
    assertEquals(1, editors.length);
    assertTrue(editors[0] instanceof TextEditor);
    Editor editor = ((TextEditor)editors[0]).getEditor();
    EditorTestUtil.waitForLoading(editor);
    final FoldingModel foldingModel = editor.getFoldingModel();
    assertEquals(2, foldingModel.getAllFoldRegions().length);
    foldingModel.runBatchFoldingOperation(() -> {
      for (FoldRegion region : foldingModel.getAllFoldRegions()) {
        region.setExpanded(false);
      }
    });
    int textLength = editor.getDocument().getTextLength();
    editor.getCaretModel().moveToOffset(textLength);
    editor.getSelectionModel().setSelection(textLength - 1, textLength);

    manager.openFile(getFile("/src/1.txt"), false);
    assertEquals(1, manager.getEditors(file).length);
    editors = manager.openFile(file, false);

    assertEquals(1, editors.length);
    assertTrue(editors[0] instanceof TextEditor);
    editor = ((TextEditor)editors[0]).getEditor();
    EditorTestUtil.waitForLoading(editor);
    assertEquals(textLength, editor.getCaretModel().getOffset());
    assertEquals(textLength - 1, editor.getSelectionModel().getSelectionStart());
    assertEquals(textLength, editor.getSelectionModel().getSelectionEnd());
  }

  public void testOpenInDumbMode() {
    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new MyFileEditorProvider(), myFixture.getTestRootDisposable());
    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new MyDumbAwareProvider(), myFixture.getTestRootDisposable());
    try {
      DumbServiceImpl.getInstance(getProject()).setDumb(true);
      VirtualFile file = createFile("/src/foo.bar", new byte[]{1, 0, 2, 3});
      FileEditor[] editors = manager.openFile(file, false);
      assertEquals(ContainerUtil.map(editors, ed-> ed + " of " + ed.getClass()).toString(), 1, editors.length);
      DumbServiceImpl.getInstance(getProject()).setDumb(false);
      UIUtil.dispatchAllInvocationEvents();
      assertEquals(2, manager.getAllEditors(file).length);
    }
    finally {
      DumbServiceImpl.getInstance(getProject()).setDumb(false);
    }
  }

  public void testOpenSpecificTextEditor() {
    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint()
      .registerExtension(new MyTextEditorProvider("one", 1), myFixture.getTestRootDisposable());
    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint()
      .registerExtension(new MyTextEditorProvider("two", 2), myFixture.getTestRootDisposable());
    Project project = getProject();
    VirtualFile file = getFile("/src/Test.java");
    manager.openTextEditor(new OpenFileDescriptor(project, file, 1), true);
    assertEquals("one", manager.getSelectedEditor(file).getName());
    manager.openTextEditor(new OpenFileDescriptor(project, file, 2), true);
    assertEquals("two", manager.getSelectedEditor(file).getName());
  }

  public void testHideDefaultEditor() {
    VirtualFile file = createFile("/src/foo.bar", new byte[]{1, 0, 2, 3});

    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new MyDefaultEditorProvider(
      "t_default", "default"), myFixture.getTestRootDisposable());

    manager.openFile(file, false);
    assertOpenedFileEditorsNames(file, "default");
    manager.closeAllFiles();

    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new MyDumbAwareProvider(
      "t_hide_def_1", "hide_def_1", FileEditorPolicy.HIDE_DEFAULT_EDITOR), myFixture.getTestRootDisposable());

    manager.openFile(file, false);
    assertOpenedFileEditorsNames(file, "hide_def_1");
    manager.closeAllFiles();

    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new MyDumbAwareProvider(
      "t_hide_def_2", "hide_def_2", FileEditorPolicy.HIDE_DEFAULT_EDITOR), myFixture.getTestRootDisposable());

    manager.openFile(file, false);
    assertOpenedFileEditorsNames(file, "hide_def_1", "hide_def_2");
    manager.closeAllFiles();

    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new MyDumbAwareProvider(
      "t_passive", "passive", FileEditorPolicy.NONE), myFixture.getTestRootDisposable());

    manager.openFile(file, false);
    assertOpenedFileEditorsNames(file, "hide_def_1", "hide_def_2", "passive");
    assertEquals(3, manager.getAllEditors(file).length);
  }

  public void testHideOtherEditors() {
    VirtualFile file = createFile("/src/foo.bar", new byte[]{1, 0, 2, 3});

    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new MyDefaultEditorProvider(
      "t_default", "default"), myFixture.getTestRootDisposable());
    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new MyFileEditorProvider(
      "t_passive", "passive", FileEditorPolicy.NONE), myFixture.getTestRootDisposable());
    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new MyDumbAwareProvider(
      "t_hide_default", "hide_default", FileEditorPolicy.HIDE_DEFAULT_EDITOR), myFixture.getTestRootDisposable());
    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new MyDumbAwareProvider(
      "t_hide_others_1", "hide_others_1", FileEditorPolicy.HIDE_OTHER_EDITORS), myFixture.getTestRootDisposable());

    manager.openFile(file, false);
    assertOpenedFileEditorsNames(file, "hide_others_1");
    manager.closeAllFiles();

    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new MyDumbAwareProvider(
      "t_hide_others_2", "hide_others_2", FileEditorPolicy.HIDE_OTHER_EDITORS), myFixture.getTestRootDisposable());
    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new MyDumbAwareProvider(
      "t_hide_others_3", "hide_others_3", FileEditorPolicy.HIDE_OTHER_EDITORS), myFixture.getTestRootDisposable());

    manager.openFile(file, false);
    assertOpenedFileEditorsNames(file, "hide_others_1", "hide_others_2", "hide_others_3");
  }

  public void testDontOpenInActiveSplitter() {
    VirtualFile file = getFile("/src/1.txt");
    VirtualFile file2 = getFile("/src/2.txt");
    manager.openFile(file, false);
    EditorWindow primaryWindow = manager.getCurrentWindow();
    assertNotNull(primaryWindow);
    manager.createSplitter(SwingConstants.VERTICAL, primaryWindow);
    EditorWindow secondaryWindow = manager.getNextWindow(primaryWindow);
    manager.openFileImpl2(secondaryWindow, file2, new FileEditorOpenOptions().withRequestFocus(true));
    manager.closeFile(file, secondaryWindow, true);

    // default behavior is to reuse the existing splitter
    new OpenFileDescriptor(getProject(), file).navigate(true);
    assertEquals(1, secondaryWindow.getTabCount());
  }

  public void testOpenInActiveSplitter() {
    ((AdvancedSettingsImpl) AdvancedSettings.getInstance()).setSetting(FileEditorManagerImpl.EDITOR_OPEN_INACTIVE_SPLITTER, false, getTestRootDisposable());

    VirtualFile file = getFile("/src/1.txt");
    VirtualFile file2 = getFile("/src/2.txt");
    manager.openFile(file, false);
    EditorWindow primaryWindow = manager.getCurrentWindow();
    assertNotNull(primaryWindow);
    manager.createSplitter(SwingConstants.VERTICAL, primaryWindow);
    EditorWindow secondaryWindow = manager.getNextWindow(primaryWindow);
    manager.openFileImpl2(secondaryWindow, file2, new FileEditorOpenOptions().withRequestFocus(true));
    manager.closeFile(file, secondaryWindow, true);

    // with the changed setting, we want to open the file in the current splitter (
    new OpenFileDescriptor(getProject(), file).navigate(true);
    assertEquals(2, secondaryWindow.getTabCount());
  }

  public void testOpenInActiveSplitterOverridesReuseOpen() {
    ((AdvancedSettingsImpl) AdvancedSettings.getInstance()).setSetting(FileEditorManagerImpl.EDITOR_OPEN_INACTIVE_SPLITTER, false, getTestRootDisposable());

    VirtualFile file = getFile("/src/1.txt");
    VirtualFile file2 = getFile("/src/2.txt");
    manager.openFile(file, false);
    EditorWindow primaryWindow = manager.getCurrentWindow();
    assertNotNull(primaryWindow);
    manager.createSplitter(SwingConstants.VERTICAL, primaryWindow);
    EditorWindow secondaryWindow = manager.getNextWindow(primaryWindow);
    manager.openFileImpl2(secondaryWindow, file2, new FileEditorOpenOptions().withRequestFocus(true));
    manager.closeFile(file, secondaryWindow, true);

    // with the changed setting, we want to open the file in the current splitter (
    new OpenFileDescriptor(getProject(), file).setUseCurrentWindow(true).navigate(true);
    assertEquals(2, secondaryWindow.getTabCount());
  }

  @Language("XML")
  private static final String STRING = """
    <component name="FileEditorManager">
        <leaf>
          <file pinned="false" current="false" current-in-tab="false">
            <entry file="file://$PROJECT_DIR$/src/1.txt">
              <provider selected="true" editor-type-id="text-editor">
                <state line="0" column="0" selection-start="0" selection-end="0" vertical-scroll-proportion="0.0">
                </state>
              </provider>
            </entry>
          </file>
          <file pinned="true" current="false" current-in-tab="false">
            <entry file="file://$PROJECT_DIR$/src/foo.xml">
              <provider selected="true" editor-type-id="text-editor">
                <state line="0" column="0" selection-start="0" selection-end="0" vertical-scroll-proportion="0.0">
                </state>
              </provider>
            </entry>
          </file>
          <file pinned="false" current="true" current-in-tab="true">
            <entry file="file://$PROJECT_DIR$/src/2.txt">
              <provider selected="true" editor-type-id="text-editor">
                <state line="0" column="0" selection-start="0" selection-end="0" vertical-scroll-proportion="0.0">
                </state>
              </provider>
            </entry>
          </file>
          <file pinned="false" current="false" current-in-tab="false">
            <entry file="file://$PROJECT_DIR$/src/3.txt">
              <provider selected="true" editor-type-id="text-editor">
                <state line="0" column="0" selection-start="0" selection-end="0" vertical-scroll-proportion="0.0">
                </state>
              </provider>
            </entry>
          </file>
        </leaf>
      </component>
    """;

  private void assertOpenFiles(String... fileNames) {
    List<String> names = ContainerUtil.map(manager.getSplitters().getEditorComposites(), composite -> composite.getFile().getName());
    assertEquals(Arrays.asList(fileNames), names);
  }

  private void assertOpenedFileEditorsNames(VirtualFile file, String... allNames) {
    FileEditor[] editors = manager.getEditors(file);
    assertEquals(allNames.length, editors.length);

    List<String> expectedNames = Arrays.asList(allNames);
    List<String> actualNames = ContainerUtil.map(editors, FileEditor::getName);
    if (!actualNames.containsAll(expectedNames)) {
      fail("Expected file editors names:\n"+expectedNames+ "\nActual names:\n"+actualNames);
    }
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getPlatformTestDataPath() + "fileEditorManager";
  }

  static class MyFileEditorProvider implements FileEditorProvider {
    static final String DEFAULT_FILE_EDITOR_NAME = "MockEditor";

    private final String myEditorTypeId;
    private final String myFileEditorName;
    private final FileEditorPolicy myPolicy;

    MyFileEditorProvider() {
      this("mock");
    }

    MyFileEditorProvider(String editorTypeId) {
      this(editorTypeId, DEFAULT_FILE_EDITOR_NAME, FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR);
    }

    MyFileEditorProvider(String editorTypeId, String fileEditorName, FileEditorPolicy policy) {
      myEditorTypeId = editorTypeId;
      myFileEditorName = fileEditorName;
      myPolicy = policy;
    }

    @NotNull
    @Override
    public String getEditorTypeId() {
      return myEditorTypeId;
    }

    @Override
    public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
      return true;
    }

    @NotNull
    @Override
    public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
      return new Mock.MyFileEditor() {
        @NotNull
        @Override
        public JComponent getComponent() {
          return new JLabel();
        }

        @NotNull
        @Override
        public String getName() {
          return myFileEditorName;
        }

        @Override
        public @NotNull VirtualFile getFile() {
          return file;
        }
      };
    }

    @Override
    public void disposeEditor(@NotNull FileEditor editor) {
    }

    @NotNull
    @Override
    public FileEditorPolicy getPolicy() {
      return myPolicy;
    }
  }

  private static class MyDumbAwareProvider extends MyFileEditorProvider implements DumbAware {
    MyDumbAwareProvider() {
      super("dumbAware");
    }

    MyDumbAwareProvider(String editorTypeId, String fileEditorName, FileEditorPolicy policy) {
      super(editorTypeId, fileEditorName, policy);
    }
  }

  private static class MyDefaultEditorProvider extends MyFileEditorProvider implements DefaultPlatformFileEditorProvider {
    MyDefaultEditorProvider(String editorTypeId, String fileEditorName) {
      super(editorTypeId, fileEditorName, FileEditorPolicy.NONE);
    }
  }

  private static final class MyTextEditorProvider implements FileEditorProvider, DumbAware {
    private final String myId;
    private final int myTargetOffset;

    private MyTextEditorProvider(String id, int targetOffset) {
      myId = id;
      myTargetOffset = targetOffset;
    }

    @Override
    public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
      return true;
    }

    @NotNull
    @Override
    public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
      return new MyTextEditor(file, FileDocumentManager.getInstance().getDocument(file), myId, myTargetOffset);
    }

    @NotNull
    @Override
    public String getEditorTypeId() {
      return myId;
    }

    @NotNull
    @Override
    public FileEditorPolicy getPolicy() {
      return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
    }
  }

  private static final class MyTextEditor extends Mock.MyFileEditor implements TextEditor {
    private final VirtualFile myFile;
    private final Editor myEditor;
    private final String myName;
    private final int myTargetOffset;

    private MyTextEditor(VirtualFile file,
                         Document document,
                         String name,
                         int targetOffset) {
      myFile = file;
      myEditor = EditorFactory.getInstance().createEditor(document);
      myName = name;
      myTargetOffset = targetOffset;
    }

    @Override
    public void dispose() {
      try {
        EditorFactory.getInstance().releaseEditor(myEditor);
      }
      finally {
        super.dispose();
      }
    }

    @NotNull
    @Override
    public JComponent getComponent() {
      return new JLabel();
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @NotNull
    @Override
    public Editor getEditor() {
      return myEditor;
    }

    @Override
    public boolean canNavigateTo(@NotNull Navigatable navigatable) {
      return navigatable instanceof OpenFileDescriptor && ((OpenFileDescriptor)navigatable).getOffset() == myTargetOffset;
    }

    @Override
    public void navigateTo(@NotNull Navigatable navigatable) {}

    @Override
    public @NotNull VirtualFile getFile() {
      return myFile;
    }
  }

  public void testMustNotAllowToTypeIntoFileRenamedToUnknownExtension() throws Exception {
    File ioFile = IoTestUtil.createTestFile("test.txt", "");
    FileUtil.writeToFile(ioFile, new byte[]{1,2,3,4,29}); // to convince IDEA it's binary when renamed to unknown extension
    VirtualFile file = Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile));
    assertEquals(PlainTextFileType.INSTANCE, file.getFileType());
    FileEditorManager.getInstance(getProject()).openFile(file, true);
    HeavyPlatformTestCase.rename(file, "test.unkneownExtensiosn");
    assertEquals(UnknownFileType.INSTANCE, file.getFileType());
    assertFalse(FileEditorManager.getInstance(getProject()).isFileOpen(file)); // must close
  }
}