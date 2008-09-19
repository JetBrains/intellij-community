package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.actions.CodeCompletionAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.testcases.action.ActionTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * author ven
 */
public abstract class CompletionTestBase extends ActionTestCase {
  protected Editor myEditor;
  protected FileEditorManager myFileEditorManager;
  protected PsiFile myFile;
  private static final PrefixMatcher TRUE_MATCHER = new MyTruePrefixMatcher();
  protected static final String RULEZZZ = "IntellijIdeaRulezzz";

  public CompletionTestBase(String path) {
    super(path);
  }

  protected CodeInsightActionHandler getCompetionHandler() {
    CodeCompletionAction action = new CodeCompletionAction();
    CodeInsightActionHandler handler = action.getHandler();
    return handler;
  }

  protected String processFile(final PsiFile file) throws IncorrectOperationException, InvalidDataException, IOException {
    //  todo [DIANA] uncomment me!
    String fileText = file.getText();
    int offset = fileText.indexOf(CARET_MARKER);
    fileText = removeCaretMarker(fileText);
    myFile = createFile(fileText);
    assert myFile != null;
    myFileEditorManager = FileEditorManager.getInstance(myProject);
    VirtualFile virtualFile = myFile.getVirtualFile();
    assert virtualFile != null;
    myEditor = myFileEditorManager.openTextEditor(new OpenFileDescriptor(myProject, virtualFile, 0), false);
    Assert.assertNotNull(myEditor);
    myEditor.getCaretModel().moveToOffset(offset);

    final CodeInsightActionHandler handler = getCompetionHandler();
    CompletionData data = CompletionUtil.getCompletionDataByElement(myFile);
    LookupItem[] items = getAcceptableItems(data);
    boolean old = CodeInsightSettings.getInstance().AUTOCOMPLETE_COMMON_PREFIX;
    CodeInsightSettings.getInstance().AUTOCOMPLETE_COMMON_PREFIX = false;

    String result = "";
    try {
      performAction(myProject, new Runnable() {
        public void run() {
          handler.invoke(myProject, myEditor, myFile);
        }
      });

//      offset = myEditor.getCaretModel().getOffset();
//      result = myEditor.getDocument().getText();
//      result = result.substring(0, offset) + CARET_MARKER + result.substring(offset);

      if (items.length > 0) {
        Arrays.sort(items);
        result = "";
        for (LookupItem item : items) {
          result = result + "\n" + item.getLookupString();
        }
        result = result.trim();
      }

    }
    finally {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_COMMON_PREFIX = old;
      myFileEditorManager.closeFile(virtualFile);
      myEditor = null;
    }
    return result;
  }

  protected PsiFile createFile(String fileText) throws IncorrectOperationException {
    return TestUtils.createPseudoPhysicalGroovyFile(myProject, fileText);
  }

  protected abstract LookupItem[] getAcceptableItems(CompletionData data) throws IncorrectOperationException;


  /**
   * retrurns acceptable variant for this completion
   *
   * @param completionData
   * @return
   */
  protected LookupItem[] getAcceptableItemsImpl(CompletionData completionData) throws IncorrectOperationException {

    // todo [DIANA] uncomment me
    final Set<LookupElement> lookupSet = new LinkedHashSet<LookupElement>();
    /**
     * Create fake file with dummy element
     */
    String newFileText = myFile.getText().substring(0, myOffset + 1) + RULEZZZ +
            myFile.getText().substring(myOffset + 1);
    /**
     * Hack for IDEA completion
     */
    PsiFile newFile = createFile(newFileText);
    CompletionContext context = new CompletionContext(myProject, myEditor, newFile, new OffsetMap(myEditor.getDocument())) {
      @Override
      public int getStartOffset() {
        return myOffset + 1;
      }
    };

    PsiElement insertedElement = newFile.findElementAt(myOffset + 1);
    if (lookupSet.isEmpty()) {
      final PsiReference ref = newFile.findReferenceAt(myOffset + 1);
      if (addKeywords(ref)) {
        // Do not duplicate reference & keyword variants for Grails tags
        final Set<CompletionVariant> keywordVariants = new HashSet<CompletionVariant>();
        completionData.addKeywordVariants(keywordVariants, insertedElement, myFile);
        insertedElement.putUserData(CompletionContext.COMPLETION_CONTEXT_KEY, context);
        completionData.completeKeywordsBySet(lookupSet, keywordVariants, insertedElement, TRUE_MATCHER, myFile);
      }
      if (ref != null && addReferenceVariants(ref)) {
        assert insertedElement != null;
        completionData.completeReference(ref, lookupSet, insertedElement, myFile, myOffset + 1);
      }
    }

    String prefix = getSpecificPrefix(insertedElement);

    ArrayList<LookupItem> lookupItems = new ArrayList<LookupItem>();
    final LookupItem[] items = lookupSet.toArray(new LookupItem[lookupSet.size()]);
    for (LookupItem item : items) {
      if (item.getLookupString().startsWith(prefix)) {
        lookupItems.add(item);
      }
    }

    return lookupItems.toArray(new LookupItem[lookupItems.size()]);

  }

  protected String getSpecificPrefix(PsiElement insertedElement) {
    String prefix = insertedElement.getText().substring(0, myOffset + 1 - insertedElement.getTextOffset());
    prefix = StringUtil.trimStart(prefix, "@");
    return StringUtil.trimEnd(prefix, RULEZZZ);
  }

  private static class MyTruePrefixMatcher extends PrefixMatcher {

    public boolean prefixMatches(@NotNull LookupElement element) {
      return true;
    }

    public boolean prefixMatches(@NotNull String name) {
      return true;
    }

    @NotNull
    public String getPrefix() {
      throw new UnsupportedOperationException("Method getPrefix is not yet implemented in " + getClass().getName());
    }

    @NotNull
    public PrefixMatcher cloneWithPrefix(@NotNull String prefix) {
      return this;
    }
  }

  public String transform(String testName, String[] data) throws Exception {
    String fileText = data[0];
    final PsiFile psiFile = createFile(fileText);
    String result = processFile(psiFile);
    //System.out.println("------------------------ " + testName + " ------------------------");
    //System.out.println(result);
    //System.out.println("");
    return result;
  }

  protected abstract boolean addKeywords(PsiReference ref);

  protected abstract boolean addReferenceVariants(PsiReference ref);
}
