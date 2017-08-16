/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.fileEditor;

import com.intellij.ide.ui.UISettings;
import com.intellij.mock.Mock;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

/**
 * @author Dmitry Avdeev
 * @author Vassiliy Kudryashov
 * Date: 4/16/13
 */
@SuppressWarnings("ConstantConditions")
public class FileEditorManagerTest extends FileEditorManagerTestCase {

  public void testTabOrder() throws Exception {

    openFiles(STRING);
    assertOpenFiles("1.txt", "foo.xml", "2.txt", "3.txt");
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      UISettings template = new UISettings();
      UISettings.getInstance().setEditorTabLimit(template.getEditorTabLimit());
      UISettings.getInstance().setReuseNotModifiedTabs(template.getReuseNotModifiedTabs());
      UISettings.getInstance().setEditorTabPlacement(template.getEditorTabPlacement());
    }
    finally {
      super.tearDown();
    }
  }

  public void testTabLimit() throws Exception {
    UISettings.getInstance().setEditorTabLimit(2);
    openFiles(STRING);
    // note that foo.xml is pinned
    assertOpenFiles("foo.xml", "3.txt");
  }

  public void testSingleTabLimit() throws Exception {
    UISettings.getInstance().setEditorTabLimit(1);
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
    UISettings.getInstance().setEditorTabLimit(2);
    UISettings.getInstance().setReuseNotModifiedTabs(false);

    myManager.openFile(getFile("/src/3.txt"), true);
    myManager.openFile(getFile("/src/foo.xml"), true);
    assertOpenFiles("3.txt", "foo.xml");
    UISettings.getInstance().setEditorTabLimit(1);
    callTrimToSize();
    assertOpenFiles("foo.xml");
    UISettings.getInstance().setEditorTabLimit(2);

    myManager.closeAllFiles();

    UISettings.getInstance().setReuseNotModifiedTabs(true);
    myManager.openFile(getFile("/src/3.txt"), true);
    assertOpenFiles("3.txt");
    myManager.openFile(getFile("/src/foo.xml"), true);
    assertOpenFiles("foo.xml");
  }

  private void callTrimToSize() {
    for (EditorsSplitters each : myManager.getAllSplitters()) {
      each.trimToSize(UISettings.getInstance().getEditorTabLimit());
    }
  }

  public void testOpenRecentEditorTab() throws Exception {
    PlatformTestUtil
      .registerExtension(FileEditorProvider.EP_FILE_EDITOR_PROVIDER, new MyFileEditorProvider(), myFixture.getTestRootDisposable());

    openFiles("  <component name=\"FileEditorManager\">\n" +
              "    <leaf>\n" +
              "      <file leaf-file-name=\"foo.xsd\" pinned=\"false\" current=\"true\" current-in-tab=\"true\">\n" +
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
    PlatformTestUtil
      .registerExtension(FileEditorProvider.EP_FILE_EDITOR_PROVIDER, new MyFileEditorProvider(), myFixture.getTestRootDisposable());
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
    assertEquals(0, myManager.getEditors(file).length);
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
    PlatformTestUtil
      .registerExtension(FileEditorProvider.EP_FILE_EDITOR_PROVIDER, new MyFileEditorProvider(), myFixture.getTestRootDisposable());
    PlatformTestUtil
      .registerExtension(FileEditorProvider.EP_FILE_EDITOR_PROVIDER, new DumbAwareProvider(), myFixture.getTestRootDisposable());
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

  private static final String STRING = "<component name=\"FileEditorManager\">\n" +
                                       "    <leaf>\n" +
                                       "      <file leaf-file-name=\"1.txt\" pinned=\"false\" current=\"false\" current-in-tab=\"false\">\n" +
                                       "        <entry file=\"file://$PROJECT_DIR$/src/1.txt\">\n" +
                                       "          <provider selected=\"true\" editor-type-id=\"text-editor\">\n" +
                                       "            <state line=\"0\" column=\"0\" selection-start=\"0\" selection-end=\"0\" vertical-scroll-proportion=\"0.0\">\n" +
                                       "            </state>\n" +
                                       "          </provider>\n" +
                                       "        </entry>\n" +
                                       "      </file>\n" +
                                       "      <file leaf-file-name=\"foo.xml\" pinned=\"true\" current=\"false\" current-in-tab=\"false\">\n" +
                                       "        <entry file=\"file://$PROJECT_DIR$/src/foo.xml\">\n" +
                                       "          <provider selected=\"true\" editor-type-id=\"text-editor\">\n" +
                                       "            <state line=\"0\" column=\"0\" selection-start=\"0\" selection-end=\"0\" vertical-scroll-proportion=\"0.0\">\n" +
                                       "            </state>\n" +
                                       "          </provider>\n" +
                                       "        </entry>\n" +
                                       "      </file>\n" +
                                       "      <file leaf-file-name=\"2.txt\" pinned=\"false\" current=\"true\" current-in-tab=\"true\">\n" +
                                       "        <entry file=\"file://$PROJECT_DIR$/src/2.txt\">\n" +
                                       "          <provider selected=\"true\" editor-type-id=\"text-editor\">\n" +
                                       "            <state line=\"0\" column=\"0\" selection-start=\"0\" selection-end=\"0\" vertical-scroll-proportion=\"0.0\">\n" +
                                       "            </state>\n" +
                                       "          </provider>\n" +
                                       "        </entry>\n" +
                                       "      </file>\n" +
                                       "      <file leaf-file-name=\"3.txt\" pinned=\"false\" current=\"false\" current-in-tab=\"false\">\n" +
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
    EditorWithProviderComposite[] files = myManager.getSplitters().getEditorsComposites();
    List<String> names = ContainerUtil.map(files, composite -> composite.getFile().getName());
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
}

