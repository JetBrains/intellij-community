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

package org.jetbrains.plugins.groovy.lang.completion;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.testcases.action.ActionTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.actions.CodeCompletionAction;
import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.util.IncorrectOperationException;


import java.io.IOException;
import java.util.*;

import junit.framework.Test;

/**
 * @author ilyas
 */
public class CompletionActionTest extends ActionTestCase {

  @NonNls
  private static final String DATA_PATH = "test/org/jetbrains/plugins/groovy/lang/completion/data/";

  protected Editor myEditor;
  protected FileEditorManager fileEditorManager;
  protected String newDocumentText;
  protected PsiFile myFile;

  public CompletionActionTest() {
    super(System.getProperty("path") != null ?
        System.getProperty("path") :
        DATA_PATH
    );
  }


  protected CodeInsightActionHandler getCompetionHandler() {
    CodeCompletionAction action = new CodeCompletionAction();
    return action.getHandler();
  }


  private String processFile(final PsiFile file) throws IncorrectOperationException, InvalidDataException, IOException {
    String result = "";
    String fileText = file.getText();
    int offset = fileText.indexOf(CARET_MARKER);
    fileText = removeCaretMarker(fileText);
    myFile = TestUtils.createPseudoPhysicalFile(project, fileText);
    fileEditorManager = FileEditorManager.getInstance(project);
    myEditor = fileEditorManager.openTextEditor(new OpenFileDescriptor(project, myFile.getVirtualFile(), 0), false);
    myEditor.getCaretModel().moveToOffset(offset);

    final CodeInsightActionHandler handler = getCompetionHandler();
    final CompletionContext context = new CompletionContext(project, myEditor, myFile, 0, myOffset);
    CompletionData data = CompletionUtil.getCompletionDataByElement(myFile.findElementAt(myOffset), context);
    LookupItem[] items = getAcceptableItems(data);

    try {
      performAction(project, new Runnable() {
        public void run() {
          handler.invoke(project, myEditor, myFile);
        }
      });

      offset = myEditor.getCaretModel().getOffset();
/*
      result = myEditor.getDocument().getText();
      result = result.substring(0, offset) + CARET_MARKER + result.substring(offset);
*/

      if (items.length > 0) {
        Arrays.sort(items);
        result = "";
        for (LookupItem item : items) {
          result = result + "\n" + item.getLookupString();
        }
        result = result.trim();
      }

    } finally {
      fileEditorManager.closeFile(myFile.getVirtualFile());
      myEditor = null;
    }
    return result;
  }

  /**
   * retrurns acceptable variant for this completion
   *
   * @param completionData
   * @return
   */
  protected LookupItem[] getAcceptableItems(CompletionData completionData) {

    final Set<LookupItem> lookupSet = new LinkedHashSet<LookupItem>();
    final PsiElement elem = myFile.findElementAt(myOffset);

    /**
     * Create fake file with dummy element
     */
    String newFileText = myFile.getText().substring(0, myOffset + 1) + "IntellijIdeaRulezzz" +
        myFile.getText().substring(myOffset + 1);
    try {
      /**
       * Hack for IDEA completion
       */
      PsiFile newFile = TestUtils.createPseudoPhysicalFile(project, newFileText);
      PsiElement insertedElement = newFile.findElementAt(myOffset + 1);
      final int offset1 =
          myEditor.getSelectionModel().hasSelection() ? myEditor.getSelectionModel().getSelectionStart() : myEditor.getCaretModel().getOffset();
      final int offset2 = myEditor.getSelectionModel().hasSelection() ? myEditor.getSelectionModel().getSelectionEnd() : offset1;
      final CompletionContext context = new CompletionContext(project, myEditor, myFile, offset1, offset2);
      context.setPrefix(elem, context.startOffset, completionData);

      if (lookupSet.size() == 0) {
        final Set<CompletionVariant> keywordVariants = new HashSet<CompletionVariant>();
        completionData.addKeywordVariants(keywordVariants, context, insertedElement);
        CompletionData.completeKeywordsBySet(lookupSet, keywordVariants, context, insertedElement);
        final PsiReference ref = newFile.findReferenceAt(myOffset + 1);
        if (ref != null) {
          completionData.completeReference(ref, lookupSet, context, insertedElement);
        }
      }

      ArrayList<LookupItem> lookupItems = new ArrayList<LookupItem>();
      final LookupItem[] items = lookupSet.toArray(new LookupItem[lookupSet.size()]);
      for (LookupItem item : items) {
        if (CompletionUtil.checkName(item, context, false)) {
          lookupItems.add(item);
        }
      }

      return lookupItems.toArray(new LookupItem[0]);
    } catch (IncorrectOperationException e) {
      e.printStackTrace();
      return new LookupItem[0];
    }
  }

  public String transform(String testName, String[] data) throws Exception {
    setSettings();
    String fileText = data[0];
    final PsiFile psiFile = TestUtils.createPseudoPhysicalFile(project, fileText);
    String result = processFile(psiFile);
    System.out.println("------------------------ " + testName + " ------------------------");
    System.out.println(result);
    System.out.println("");
    return result;
  }


  public static Test suite() {
    return new CompletionActionTest();
  }
}