package org.jetbrains.plugins.groovy.lang.completion;

import org.jetbrains.plugins.groovy.testcases.action.ActionTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;
import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.completion.actions.CodeCompletionAction;
import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.codeInsight.completion.CompletionData;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.CompletionVariant;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.editor.Editor;

import java.io.IOException;
import java.util.*;

/**
 * author ven
 */
public abstract class CompletionTestBase extends ActionTestCase {
  protected Editor myEditor;
  protected FileEditorManager myFileEditorManager;
  protected PsiFile myFile;

  public CompletionTestBase(String path) {
    super(path);
  }

  protected CodeInsightActionHandler getCompetionHandler() {
    CodeCompletionAction action = new CodeCompletionAction();
    return action.getHandler();
  }

  protected String processFile(final PsiFile file) throws IncorrectOperationException, InvalidDataException, IOException {
    String result = "";
    String fileText = file.getText();
    int offset = fileText.indexOf(CARET_MARKER);
    fileText = removeCaretMarker(fileText);
    myFile = TestUtils.createPseudoPhysicalFile(myProject, fileText);
    myFileEditorManager = FileEditorManager.getInstance(myProject);
    myEditor = myFileEditorManager.openTextEditor(new OpenFileDescriptor(myProject, myFile.getVirtualFile(), 0), false);
    myEditor.getCaretModel().moveToOffset(offset);

    final CodeInsightActionHandler handler = getCompetionHandler();
    final CompletionContext context = new CompletionContext(myProject, myEditor, myFile, 0, myOffset);
    CompletionData data = CompletionUtil.getCompletionDataByElement(myFile.findElementAt(myOffset), context);
    LookupItem[] items = getAcceptableItems(data);

    try {
      performAction(myProject, new Runnable() {
        public void run() {
          handler.invoke(myProject, myEditor, myFile);
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
      myFileEditorManager.closeFile(myFile.getVirtualFile());
      myEditor = null;
    }
    return result;
  }

  protected abstract LookupItem[] getAcceptableItems(CompletionData data);


  /**
   * retrurns acceptable variant for this completion
   *
   * @param completionData
   * @param addKeywords
   * @param addReferenceVariants
   * @return
   */
  protected LookupItem[] getAcceptableItemsImpl(CompletionData completionData,
                                            boolean addKeywords,
                                            boolean addReferenceVariants) {

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
      PsiFile newFile = TestUtils.createPseudoPhysicalFile(myProject, newFileText);
      PsiElement insertedElement = newFile.findElementAt(myOffset + 1);
      final int offset1 =
          myEditor.getSelectionModel().hasSelection() ? myEditor.getSelectionModel().getSelectionStart() : myEditor.getCaretModel().getOffset();
      final int offset2 = myEditor.getSelectionModel().hasSelection() ? myEditor.getSelectionModel().getSelectionEnd() : offset1;
      final CompletionContext context = new CompletionContext(myProject, myEditor, myFile, offset1, offset2);
      context.setPrefix(elem, context.startOffset, completionData);

      if (lookupSet.size() == 0) {
        if (addKeywords) {
          final Set<CompletionVariant> keywordVariants = new HashSet<CompletionVariant>();
          completionData.addKeywordVariants(keywordVariants, context, insertedElement);
          CompletionData.completeKeywordsBySet(lookupSet, keywordVariants, context, insertedElement);
        }
        if (addReferenceVariants) {
          final PsiReference ref = newFile.findReferenceAt(myOffset + 1);
          if (ref != null) {
            completionData.completeReference(ref, lookupSet, context, insertedElement);
          }
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
    final PsiFile psiFile = TestUtils.createPseudoPhysicalFile(myProject, fileText);
    String result = processFile(psiFile);
    System.out.println("------------------------ " + testName + " ------------------------");
    System.out.println(result);
    System.out.println("");
    return result;
  }
}
