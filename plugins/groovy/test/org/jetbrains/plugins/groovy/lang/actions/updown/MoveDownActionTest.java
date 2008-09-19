/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.actions.updown;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import junit.framework.Assert;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.editor.actions.GroovyEditorActionsManager;
import org.jetbrains.plugins.groovy.testcases.action.ActionTestCase;
import org.jetbrains.plugins.groovy.util.PathUtil;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.IOException;

/**
 * @author ilyas
 */
public class MoveDownActionTest extends ActionTestCase {

  @NonNls
  private static final String DATA_PATH = PathUtil.getDataPath(MoveDownActionTest.class) + "/down";

  protected Editor myEditor;
  protected FileEditorManager fileEditorManager;
  protected String newDocumentText;
  protected PsiFile myFile;

  public MoveDownActionTest() {
    super(System.getProperty("path") != null ?
        System.getProperty("path") :
        DATA_PATH
    );
  }

  public MoveDownActionTest(String path) {
    super(path);
  }


  protected EditorActionHandler getMyHandler() {
    return EditorActionManager.getInstance().getActionHandler(GroovyEditorActionsManager.MOVE_STATEMENT_DOWN_ACTION);
  }


  private String processFile(final PsiFile file) throws IncorrectOperationException, InvalidDataException, IOException {
    String result;
    String fileText = file.getText();
    int offset = fileText.indexOf(CARET_MARKER);
    fileText = TestUtils.removeCaretMarker(fileText);
    myFile = TestUtils.createPseudoPhysicalGroovyFile(myProject, fileText);
    fileEditorManager = FileEditorManager.getInstance(myProject);
    VirtualFile virtualFile = myFile.getVirtualFile();
    assert virtualFile != null;
    myEditor = fileEditorManager.openTextEditor(new OpenFileDescriptor(myProject, virtualFile, 0), false);
    Assert.assertNotNull(myEditor);
    myEditor.getCaretModel().moveToOffset(offset);

    final myDataContext dataContext = getDataContext(myFile);
    final EditorActionHandler handler = getMyHandler();

    try {
      performAction(myProject, new Runnable() {
        public void run() {
          handler.execute(myEditor, dataContext);
        }
      });

      offset = myEditor.getCaretModel().getOffset();
      result = myEditor.getDocument().getText();
      result = result.substring(0, offset) + CARET_MARKER + result.substring(offset);
    } finally {
      fileEditorManager.closeFile(virtualFile);
      myEditor = null;
    }

    return result;
  }

  public String transform(String testName, String[] data) throws Exception {
    String fileText = data[0];
    final PsiFile psiFile = TestUtils.createPseudoPhysicalGroovyFile(myProject, fileText);
    String result = processFile(psiFile);
    //System.out.println("------------------------ " + testName + " ------------------------");
    //System.out.println(result);
    //System.out.println("");
    return result;
  }


  public static Test suite() {
    return new MoveDownActionTest();
  }
}

