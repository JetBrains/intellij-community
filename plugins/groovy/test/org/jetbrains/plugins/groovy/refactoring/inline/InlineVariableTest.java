/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.inline;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.inline.GenericInlineHandler;
import com.intellij.util.IncorrectOperationException;
import junit.framework.Assert;
import junit.framework.Test;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.refactoring.CommonRefactoringTestCase;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * @author ilyas
 */
public class InlineVariableTest extends CommonRefactoringTestCase {

  private static final String DATA_PATH = "test/org/jetbrains/plugins/groovy/refactoring/inline/data/local";

  protected static final String TEST_FILE_PATTERN = "(.*)\\.test";

  public InlineVariableTest() {
    super(System.getProperty("path") != null ?
        System.getProperty("path") :
        DATA_PATH);
  }


  protected String processFile(String fileText) throws IncorrectOperationException, InvalidDataException, IOException {
    String result = "";
    int startOffset = fileText.indexOf(BEGIN_MARKER);
    fileText = TestUtils.removeBeginMarker(fileText, startOffset);
    int endOffset = fileText.indexOf(END_MARKER);
    fileText = TestUtils.removeEndMarker(fileText, endOffset);
    PsiFile file = TestUtils.createPseudoPhysicalFile(myProject, fileText);

    Assert.assertNotNull(file);

    // Create physical file
    String modulePath = myFixture.getTempDirPath();
    File realFile = new File(modulePath + "/" + TestUtils.TEMP_FILE);
    FileWriter fstream = new FileWriter(realFile);
    BufferedWriter out = new BufferedWriter(fstream);
    out.write(file.getText());
    out.close();

    VirtualFileManager fileManager = VirtualFileManager.getInstance();
    fileManager.refresh(false);
    VirtualFile virtualFile = fileManager.findFileByUrl("file://" + modulePath + "/" + TestUtils.TEMP_FILE);

    assert virtualFile != null;

    FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
    Editor myEditor = fileEditorManager.openTextEditor(new OpenFileDescriptor(myProject, virtualFile, 0), false);
    assert myEditor != null;


    file = PsiManager.getInstance(myProject).findFile(virtualFile);
    Assert.assertTrue(file instanceof GroovyFile);

    try {
      myEditor.getSelectionModel().setSelection(startOffset, endOffset);
      myEditor.getCaretModel().moveToOffset(endOffset);

      GroovyPsiElement selectedArea = GroovyRefactoringUtil.findElementInRange(((GroovyFile) file), startOffset, endOffset, GrReferenceExpression.class);
      if (selectedArea == null) {
        PsiElement identifier = GroovyRefactoringUtil.findElementInRange(((GroovyFile) file), startOffset,
            endOffset, PsiElement.class);
        if (identifier != null){
          Assert.assertTrue("Selected area doesn't point to variable", identifier.getParent() instanceof GrVariable);
          selectedArea = (GrVariable)identifier.getParent();
        }
      }
      Assert.assertNotNull("Selected area reference points to nothing", selectedArea);
      PsiElement element = selectedArea instanceof GrExpression ? selectedArea.getReference().resolve() : selectedArea;
      Assert.assertNotNull("Cannot resolve selected reference expression", element);
      Assert.assertTrue(element instanceof GrVariable);

      // handling inline refactoring
      GenericInlineHandler.invoke(element, myEditor, new GroovyInlineHandler());
      result = myEditor.getDocument().getText();


      int caretOffset = myEditor.getCaretModel().getOffset();
      result = result.substring(0, caretOffset) + CARET_MARKER + result.substring(caretOffset);
    } finally {
      fileEditorManager.closeFile(virtualFile);
      realFile.delete();
      myEditor = null;
    }

    return result;
  }

  public static Test suite() {
    return new InlineVariableTest();
  }


}
