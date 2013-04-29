/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @author Dmitry Avdeev
 *         Date: 4/16/13
 */
public class FileEditorManagerTest extends FileEditorManagerTestCase {

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public FileEditorManagerTest() {
    PlatformTestCase.initPlatformLangPrefix();
  }

  public void testTabOrder() throws Exception {

    openFiles(STRING);
    assertOpenFiles("1.txt", "foo.xml", "2.txt", "3.txt");
  }

  public void testTabLimit() throws Exception {

    int limit = UISettings.getInstance().EDITOR_TAB_LIMIT;
    try {
      UISettings.getInstance().EDITOR_TAB_LIMIT = 2;
      openFiles(STRING);
      // note that foo.xml is pinned
      assertOpenFiles("foo.xml", "3.txt");
    }
    finally {
      UISettings.getInstance().EDITOR_TAB_LIMIT = limit;
    }
  }

  public void testOpenRecentEditorTab() throws Exception {
    PlatformTestUtil.registerExtension(FileEditorProvider.EP_FILE_EDITOR_PROVIDER, new MyFileEditorProvider(), getTestRootDisposable());

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

  public void testTrackSelectedEditor() throws Exception {
    PlatformTestUtil.registerExtension(FileEditorProvider.EP_FILE_EDITOR_PROVIDER, new MyFileEditorProvider(), getTestRootDisposable());
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
    List<String> names = ContainerUtil.map(files, new Function<EditorWithProviderComposite, String>() {
      @Override
      public String fun(EditorWithProviderComposite composite) {
        return composite.getFile().getName();
      }
    });
    assertEquals(Arrays.asList(fileNames), names);
  }

  private void openFiles(String s) throws IOException, JDOMException, InterruptedException, ExecutionException {
    Document document = JDOMUtil.loadDocument(s);
    Element rootElement = document.getRootElement();
    ExpandMacroToPathMap map = new ExpandMacroToPathMap();
    map.addMacroExpand(PathMacroUtil.PROJECT_DIR_MACRO_NAME, getTestDataPath());
    map.substitute(rootElement, true, true);

    myManager.readExternal(rootElement);

    Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        myManager.getMainSplitters().openFiles();
      }
    });
    future.get();
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/platform/platform-tests/testData/fileEditorManager";
  }

  static class MyFileEditorProvider implements FileEditorProvider {
    @NotNull
    @Override
    public String getEditorTypeId() {
      return "mock";
    }

    @NotNull
    @Override
    public FileEditorState readState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
      return FileEditorState.INSTANCE;
    }

    @Override
    public void writeState(@NotNull FileEditorState state, @NotNull Project project, @NotNull Element targetElement) {
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
}
