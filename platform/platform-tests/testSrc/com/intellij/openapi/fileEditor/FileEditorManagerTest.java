// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsState;
import com.intellij.mock.Mock;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

public class FileEditorManagerTest extends FileEditorManagerTestCase {
  public void testTabOrder() throws Exception {

    openFiles(STRING);
    assertOpenFiles("1.txt", "foo.xml", "2.txt", "3.txt");
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

  public void testSingleTabLimit() throws Exception {
    UISettings.getInstance().getState().setEditorTabLimit(1);
    openFiles(STRING.replace("pinned=\"true\"", "pinned=\"false\""));
    assertOpenFiles("3.txt");

    myManager.closeAllFiles();

    openFiles(STRING);
    // note that foo.xml is pinned
    assertOpenFiles("foo.xml");
    myManager.openFile(getFile("/src/3.txt"), true);
    assertOpenFiles("3.txt", "foo.xml");//limit is still 1 but pinned prevent closing tab and actual tab number may exceed the limit

    myManager.closeAllFiles();

    myManager.openFile(getFile("/src/3.txt"), true);
    myManager.openFile(getFile("/src/foo.xml"), true);
    assertOpenFiles("foo.xml");
    callTrimToSize();
    assertOpenFiles("foo.xml");
  }

  public void testReuseNotModifiedTabs() {
    UISettingsState uiSettings = UISettings.getInstance().getState();
    uiSettings.setEditorTabLimit(2);
    uiSettings.setReuseNotModifiedTabs(false);

    myManager.openFile(getFile("/src/3.txt"), true);
    myManager.openFile(getFile("/src/foo.xml"), true);
    assertOpenFiles("3.txt", "foo.xml");
    uiSettings.setEditorTabLimit(1);
    callTrimToSize();
    assertOpenFiles("foo.xml");
    uiSettings.setEditorTabLimit(2);

    myManager.closeAllFiles();

    uiSettings.setReuseNotModifiedTabs(true);
    myManager.openFile(getFile("/src/3.txt"), true);
    assertOpenFiles("3.txt");
    myManager.openFile(getFile("/src/foo.xml"), true);
    assertOpenFiles("foo.xml");
  }

  private void callTrimToSize() {
    for (EditorsSplitters each : myManager.getAllSplitters()) {
      each.trimToSize();
    }
  }

  public void testOpenRecentEditorTab() throws Exception {
    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new MyFileEditorProvider(), myFixture.getTestRootDisposable());

    openFiles("  <component name=\"FileEditorManager\">\n" +
              "    <leaf>\n" +
              "      <file pinned=\"false\" current=\"true\" current-in-tab=\"true\">\n" +
              "        <entry selected=\"true\" file=\"file://$PROJECT_DIR$/src/1.txt\">\n" +
              "          <provider editor-type-id=\"mock\" selected=\"true\">\n" +
              "            <state />\n" +
              "          </provider>\n" +
              "          <provider editor-type-id=\"text-editor\">\n" +
              "            <state/>\n" +
              "          </provider>\n" +
              "        </entry>\n" +
              "      </file>\n" +
              "    </leaf>\n" +
              "  </component>\n");
    FileEditor[] selectedEditors = myManager.getSelectedEditors();
    assertEquals(1, selectedEditors.length);
    assertEquals("mockEditor", selectedEditors[0].getName());
  }

  public void testTrackSelectedEditor() {
    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new MyFileEditorProvider(), myFixture.getTestRootDisposable());
    VirtualFile file = getFile("/src/1.txt");
    assertNotNull(file);
    FileEditor[] editors = myManager.openFile(file, true);
    assertEquals(2, editors.length);
    assertEquals("Text", myManager.getSelectedEditor(file).getName());
    myManager.setSelectedEditor(file, "mock");
    assertEquals("mockEditor", myManager.getSelectedEditor(file).getName());

    VirtualFile file1 = getFile("/src/2.txt");
    myManager.openFile(file1, true);
    assertEquals("mockEditor", myManager.getSelectedEditor(file).getName());
  }

  public void testWindowClosingRetainsOtherWindows() {
    VirtualFile file = getFile("/src/1.txt");
    assertNotNull(file);
    myManager.openFile(file, false);
    EditorWindow primaryWindow = myManager.getCurrentWindow();
    assertNotNull(primaryWindow);
    myManager.createSplitter(SwingConstants.VERTICAL, primaryWindow);
    EditorWindow secondaryWindow = myManager.getNextWindow(primaryWindow);
    assertNotNull(secondaryWindow);
    myManager.createSplitter(SwingConstants.VERTICAL, secondaryWindow);
    myManager.closeFile(file, primaryWindow);
    assertEquals(2, myManager.getWindows().length);
  }

  public void testOpenFileInTablessSplitter() {
    VirtualFile file1 = getFile("/src/1.txt");
    assertNotNull(file1);
    file1.putUserData(EditorWindow.INITIAL_INDEX_KEY, null);
    myManager.openFile(file1, false);
    VirtualFile file2 = getFile("/src/2.txt");
    file2.putUserData(EditorWindow.INITIAL_INDEX_KEY, null);
    assertNotNull(file2);
    myManager.openFile(file2, true);
    EditorWindow primaryWindow = myManager.getCurrentWindow();//1.txt and selected 2.txt
    assertNotNull(primaryWindow);
    myManager.createSplitter(SwingConstants.VERTICAL, primaryWindow);
    EditorWindow secondaryWindow = myManager.getNextWindow(primaryWindow);//2.txt only, selected and focused
    assertNotNull(secondaryWindow);
    UISettings.getInstance().setEditorTabPlacement(UISettings.TABS_NONE);
    myManager.openFileWithProviders(file1, true, true);//Here we have to ignore 'searchForSplitter'
    assertEquals(2, primaryWindow.getTabCount());
    assertEquals(2, secondaryWindow.getTabCount());
    assertOrderedEquals(primaryWindow.getFiles(), file1, file2);
    assertOrderedEquals(secondaryWindow.getFiles(), file2, file1);
  }

  public void testStoringCaretStateForFileWithFoldingsWithNoTabs() {
    UISettings.getInstance().setEditorTabPlacement(UISettings.TABS_NONE);
    VirtualFile file = getFile("/src/Test.java");
    assertNotNull(file);
    FileEditor[] editors = myManager.openFile(file, false);
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

    myManager.openFile(getFile("/src/1.txt"), false);
    assertEquals(1, myManager.getEditors(file).length);
    editors = myManager.openFile(file, false);

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
    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new DumbAwareProvider(), myFixture.getTestRootDisposable());
    try {
      DumbServiceImpl.getInstance(getProject()).setDumb(true);
      VirtualFile file = getFile("/src/foo.bar");
      assertEquals(1, myManager.openFile(file, false).length);
      DumbServiceImpl.getInstance(getProject()).setDumb(false);
      UIUtil.dispatchAllInvocationEvents();
      assertEquals(2, myManager.getAllEditors(file).length);
      //assertFalse(FileEditorManagerImpl.isDumbAware(editors[0]));
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
    myManager.openTextEditor(new OpenFileDescriptor(project, file, 1), true);
    assertEquals("one", myManager.getSelectedEditor(file).getName());
    myManager.openTextEditor(new OpenFileDescriptor(project, file, 2), true);
    assertEquals("two", myManager.getSelectedEditor(file).getName());
  }

  private static final String STRING = "<component name=\"FileEditorManager\">\n" +
                                       "    <leaf>\n" +
                                       "      <file pinned=\"false\" current=\"false\" current-in-tab=\"false\">\n" +
                                       "        <entry file=\"file://$PROJECT_DIR$/src/1.txt\">\n" +
                                       "          <provider selected=\"true\" editor-type-id=\"text-editor\">\n" +
                                       "            <state line=\"0\" column=\"0\" selection-start=\"0\" selection-end=\"0\" vertical-scroll-proportion=\"0.0\">\n" +
                                       "            </state>\n" +
                                       "          </provider>\n" +
                                       "        </entry>\n" +
                                       "      </file>\n" +
                                       "      <file pinned=\"true\" current=\"false\" current-in-tab=\"false\">\n" +
                                       "        <entry file=\"file://$PROJECT_DIR$/src/foo.xml\">\n" +
                                       "          <provider selected=\"true\" editor-type-id=\"text-editor\">\n" +
                                       "            <state line=\"0\" column=\"0\" selection-start=\"0\" selection-end=\"0\" vertical-scroll-proportion=\"0.0\">\n" +
                                       "            </state>\n" +
                                       "          </provider>\n" +
                                       "        </entry>\n" +
                                       "      </file>\n" +
                                       "      <file pinned=\"false\" current=\"true\" current-in-tab=\"true\">\n" +
                                       "        <entry file=\"file://$PROJECT_DIR$/src/2.txt\">\n" +
                                       "          <provider selected=\"true\" editor-type-id=\"text-editor\">\n" +
                                       "            <state line=\"0\" column=\"0\" selection-start=\"0\" selection-end=\"0\" vertical-scroll-proportion=\"0.0\">\n" +
                                       "            </state>\n" +
                                       "          </provider>\n" +
                                       "        </entry>\n" +
                                       "      </file>\n" +
                                       "      <file pinned=\"false\" current=\"false\" current-in-tab=\"false\">\n" +
                                       "        <entry file=\"file://$PROJECT_DIR$/src/3.txt\">\n" +
                                       "          <provider selected=\"true\" editor-type-id=\"text-editor\">\n" +
                                       "            <state line=\"0\" column=\"0\" selection-start=\"0\" selection-end=\"0\" vertical-scroll-proportion=\"0.0\">\n" +
                                       "            </state>\n" +
                                       "          </provider>\n" +
                                       "        </entry>\n" +
                                       "      </file>\n" +
                                       "    </leaf>\n" +
                                       "  </component>\n";

  private void assertOpenFiles(String... fileNames) {
    List<String> names = ContainerUtil.map(myManager.getSplitters().getEditorComposites(), composite -> composite.getFile().getName());
    assertEquals(Arrays.asList(fileNames), names);
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getPlatformTestDataPath() + "fileEditorManager";
  }

  static class MyFileEditorProvider implements FileEditorProvider {
    @NotNull
    @Override
    public String getEditorTypeId() {
      return "mock";
    }

    @Override
    public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
      return true;
    }

    @NotNull
    @Override
    public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
      return new Mock.MyFileEditor() {
        @Override
        public boolean isValid() {
          return true;
        }

        @NotNull
        @Override
        public JComponent getComponent() {
          return new JLabel();
        }

        @NotNull
        @Override
        public String getName() {
          return "mockEditor";
        }
      };
    }

    @Override
    public void disposeEditor(@NotNull FileEditor editor) {
    }

    @NotNull
    @Override
    public FileEditorPolicy getPolicy() {
      return FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR;
    }
  }

  private static class DumbAwareProvider extends MyFileEditorProvider implements DumbAware {
    @NotNull
    @Override
    public String getEditorTypeId() {
      return "dumbAware";
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
      return new MyTextEditor(FileDocumentManager.getInstance().getDocument(file), myId, myTargetOffset);
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
    private final Editor myEditor;
    private final String myName;
    private final int myTargetOffset;

    private MyTextEditor(Document document, String name, int targetOffset) {
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
  }
}

