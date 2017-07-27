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
package com.intellij.testFramework.propertyBased;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import jetCheck.Generator;
import jetCheck.IntDistribution;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public class InvokeCompletion extends ActionOnRange {
  private final int myItemIndexRaw;
  private final char myCompletionChar;
  private final CompletionPolicy myPolicy;
  private String myLog = "not invoked";

  InvokeCompletion(PsiFile file, int offset, int itemIndexRaw, char completionChar, CompletionPolicy policy) {
    super(file, offset, offset);
    myItemIndexRaw = itemIndexRaw;
    myCompletionChar = completionChar;
    myPolicy = policy;
  }

  @Override
  public String toString() {
    return "InvokeCompletion{" + getVirtualFile().getPath() + ", " + myLog + ", raw=" + myInitialStart + "," + myItemIndexRaw + "}";
  }

  @Override
  public void performAction() {
    Project project = getProject();
    Editor editor =
      FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, getVirtualFile(), 0), true);
    assert editor != null;

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    int offset = getFinalStartOffset();
    if (offset < 0) return;
    
    myLog = "offset=" + offset;

    editor.getCaretModel().moveToOffset(offset);
    
    MadTestingUtil.restrictChangesToDocument(editor.getDocument(), () -> {
      Disposable raiseCompletionLimit = Disposer.newDisposable();
      Registry.get("ide.completion.variant.limit").setValue(100_000, raiseCompletionLimit);
      try {
        PsiTestUtil.checkPsiStructureWithCommit(getFile(), PsiTestUtil::checkStubsMatchText);
        performCompletion(editor);
        PsiTestUtil.checkPsiStructureWithCommit(getFile(), PsiTestUtil::checkStubsMatchText);
      }
      finally {
        Disposer.dispose(raiseCompletionLimit);
        LookupManager.getInstance(project).hideActiveLookup();
        UIUtil.dispatchAllInvocationEvents();
      }
    });
  }

  private void performCompletion(Editor editor) {
    int caretOffset = editor.getCaretModel().getOffset();
    int adjustedOffset = TargetElementUtil.adjustOffset(getFile(), getDocument(), caretOffset);

    PsiElement leaf = getFile().findElementAt(adjustedOffset);
    PsiReference ref = getFile().findReferenceAt(adjustedOffset);

    String expectedVariant = leaf == null ? null : myPolicy.getExpectedVariant(editor, getFile(), leaf, ref);

    boolean prefixEqualsExpected = isPrefixEqualToExpectedVariant(caretOffset, leaf, ref, expectedVariant);

    boolean shouldCheckDuplicates = myPolicy.shouldCheckDuplicates(editor, getFile(), leaf);

    new CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(getProject(), editor);
    
    String notFound = ". Please either fix completion so that the variant is suggested, " +
                      "or, if absolutely needed, tweak CompletionPolicy to exclude it.";

    LookupEx lookup = LookupManager.getActiveLookup(editor);
    if (lookup == null) {
      myLog += ", no lookup";
      if (expectedVariant == null || prefixEqualsExpected) return;
      TestCase.fail("No lookup, but expected '" + expectedVariant + "' among completion variants" + notFound);
    }

    List<LookupElement> items = lookup.getItems();
    if (expectedVariant != null) {
      LookupElement sameItem = ContainerUtil.find(items, e -> e.getAllLookupStrings().contains(expectedVariant));
      TestCase.assertNotNull("No variant '" + expectedVariant + "' among " + items + notFound, sameItem);
    }

    if (shouldCheckDuplicates) {
      checkNoDuplicates(items);
    }

    LookupElement item = items.get(myItemIndexRaw % items.size());
    myLog += ", selected '" + item + "' with '" + StringUtil.escapeStringCharacters(String.valueOf(myCompletionChar)) + "'";
    ((LookupImpl)lookup).finishLookup(myCompletionChar, item);
  }

  private boolean isPrefixEqualToExpectedVariant(int caretOffset, PsiElement leaf, PsiReference ref, String expectedVariant) {
    if (expectedVariant == null) return false;

    int expectedEnd = ref != null ? ref.getRangeInElement().getEndOffset() + ref.getElement().getTextRange().getStartOffset() :
                      leaf != null ? leaf.getTextRange().getEndOffset() :
                      0;
    return expectedEnd == caretOffset && getFile().getText().substring(0, caretOffset).endsWith(expectedVariant);
  }

  private static void checkNoDuplicates(List<LookupElement> items) {
    Set<List<?>> presentations = new HashSet<>();
    for (LookupElement item : items) {
      LookupElementPresentation p = LookupElementPresentation.renderElement(item);
      if (seemsTruncated(p.getItemText()) || seemsTruncated(p.getTailText()) || seemsTruncated(p.getTypeText())) {
        continue;
      }

      List<Object> info = Arrays.asList(p.getItemText(), p.getItemTextForeground(), p.isItemTextBold(), p.isItemTextUnderlined(),
                                        p.getTailFragments(),
                                        p.getTypeText(), p.getTypeIcon(), p.isTypeGrayed(),
                                        p.isStrikeout());
      if (!presentations.add(info)) {
        TestCase.fail("Duplicate suggestions: " + p);
      }
    }
  }

  private static boolean seemsTruncated(String text) {
    return text != null && text.contains("...");
  }

  @NotNull
  public static Generator<InvokeCompletion> completions(PsiFile psiFile, CompletionPolicy policy) {
    return Generator.from(data -> {
      Document document = psiFile.getViewProvider().getDocument();
      assert document != null;
      int offset = data.drawInt(IntDistribution.uniform(0, document.getTextLength()));
      int itemIndex = data.drawInt(IntDistribution.uniform(0, 100));
      String selectionCharacters = policy.getPossibleSelectionCharacters();
      char c = selectionCharacters.charAt(data.drawInt(IntDistribution.uniform(0, selectionCharacters.length() - 1)));
      return new InvokeCompletion(psiFile, offset, itemIndex, c, policy);
    });
  }
}
