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

package org.jetbrains.plugins.groovy.refactoring.introduceVariable;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import junit.framework.Assert;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.testcases.action.ActionTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.IOException;

/**
 * @author ilyas
 */
public class IntroduceVariableTest extends ActionTestCase {

  @NonNls
  private static final String DATA_PATH = "test/org/jetbrains/plugins/groovy/refactoring/introduceVariable/data";

  protected static final String ALL_MARKER = "<all>";

  protected Editor myEditor;
  protected FileEditorManager fileEditorManager;
  protected String newDocumentText;
  protected PsiFile myFile;

  protected boolean replaceAllOccurences = false;

  public IntroduceVariableTest() {
    super(System.getProperty("path") != null ?
        System.getProperty("path") :
        DATA_PATH
    );
    replaceAllOccurences = System.getProperty("replaceAll") != null &&
        Boolean.parseBoolean(System.getProperty("path"));
  }


  private String processFile(final PsiFile file) throws IncorrectOperationException, InvalidDataException, IOException {
    String result = "";
    String fileText = file.getText();
    int startOffset = fileText.indexOf(TestUtils.BEGIN_MARKER);
    if (startOffset < 0) {
      startOffset = fileText.indexOf(ALL_MARKER);
      replaceAllOccurences = true;
      fileText = removeAllMarker(fileText);
    } else {
      replaceAllOccurences = false;
      fileText = TestUtils.removeBeginMarker(fileText, myOffset);
    }
    int endOffset = fileText.indexOf(TestUtils.END_MARKER);
    fileText = TestUtils.removeEndMarker(fileText, myOffset);
    myFile = TestUtils.createPseudoPhysicalFile(myProject, fileText);
    fileEditorManager = FileEditorManager.getInstance(myProject);
    myEditor = fileEditorManager.openTextEditor(new OpenFileDescriptor(myProject, myFile.getVirtualFile(), 0), false);

    try {
      myEditor.getSelectionModel().setSelection(startOffset, endOffset);

      // gathering data for introduce variable
      GroovyIntroduceVariableBase introduceVariableBase = new GroovyIntroduceVariableHandler();

      Assert.assertTrue(myFile instanceof GroovyFile);
      GrExpression selectedExpr = GroovyRefactoringUtil.findElementInRange(((GroovyFile) myFile), startOffset, endOffset, GrExpression.class);

      Assert.assertNotNull("Selected expression reference points to null", selectedExpr);

      final PsiElement tempContainer = GroovyRefactoringUtil.getEnclosingContainer(selectedExpr);
      Assert.assertTrue(tempContainer instanceof GroovyPsiElement);

      PsiElement[] occurences = GroovyRefactoringUtil.getExpressionOccurrences(GroovyRefactoringUtil.getUnparenthesizedExpr(selectedExpr), tempContainer);
      String varName = "preved";
      final boolean isFinal = false;
      final PsiType varType = null;
      final GrVariableDeclaration varDecl = GroovyElementFactory.getInstance(myProject).createVariableDeclaration(varName,
          GroovyRefactoringUtil.getUnparenthesizedExpr(selectedExpr), varType, isFinal);

      introduceVariableBase.runRefactoring(selectedExpr, myEditor, ((GroovyPsiElement) tempContainer),
          occurences, varName, varType, replaceAllOccurences, varDecl);


      result = myEditor.getDocument().getText();
      int caretOffset = myEditor.getCaretModel().getOffset();
      result = result.substring(0, caretOffset) + TestUtils.CARET_MARKER + result.substring(caretOffset);
    } finally {
      fileEditorManager.closeFile(myFile.getVirtualFile());
      myEditor = null;
    }

    return result;
  }


  public String transform(String testName, String[] data) throws Exception {
    setSettings();
    String fileText = data[0];
    final PsiFile psiFile = TestUtils.createPseudoPhysicalFile(myProject, fileText);
    String result = processFile(psiFile);
    System.out.println("------------------------ " + testName + " ------------------------");
    System.out.println(result);
    System.out.println("");
    return result;
  }


  public static Test suite() {
    return new IntroduceVariableTest();
  }

  protected String removeAllMarker(String text) {
    int index = text.indexOf(ALL_MARKER);
    myOffset = index - 1;
    return text.substring(0, index) + text.substring(index + ALL_MARKER.length());
  }

}