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

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import slowCheck.Generator;

import java.util.List;

public class InvokeIntention extends ActionOnRange {
  private static final Logger LOG = Logger.getInstance("#com.intellij.testFramework.propertyBased.InvokeIntention");
  private final int myIntentionIndex;
  private final IntentionPolicy myPolicy;
  private String myInvocationLog = "not invoked";

  InvokeIntention(PsiFile file, int offset, int intentionIndex, IntentionPolicy policy) {
    super(file, offset, offset);
    myIntentionIndex = intentionIndex;
    myPolicy = policy;
  }

  @NotNull
  public static Generator<InvokeIntention> randomIntentions(@NotNull PsiFile psiFile, @NotNull IntentionPolicy policy) {
    return Generator.zipWith(Generator.integers(0, psiFile.getTextLength()), Generator.integers(0, 100),
                             (offset, index) -> new InvokeIntention(psiFile, offset, index, policy)).noShrink();
  }

  @Override
  public String toString() {
    return "InvokeIntention{" + getVirtualFile().getPath() + ", " + myInvocationLog + ", initials=" + myInitialStart + "," + myIntentionIndex + "}";
  }

  public void performAction() {
    int offset = getStartOffset();
    myInvocationLog = "offset " + offset;
    if (offset < 0) return;

    Project project = getProject();
    Editor editor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, getVirtualFile(), offset), true);
    
    List<HighlightInfo> infos = RehighlightAllEditors.highlightEditor(editor, project);
    boolean hasErrors = infos.stream().anyMatch(i -> i.getSeverity() == HighlightSeverity.ERROR);

    PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, getProject());
    IntentionAction intention = getRandomIntention(editor, file);
    if (intention == null) return;
    myInvocationLog += ", invoke '" + intention.getText() + "'";

    Document changedDocument = getDocumentToBeChanged(intention);
    String textBefore = changedDocument == null ? null : changedDocument.getText();

    Runnable r = () -> CodeInsightTestFixtureImpl.invokeIntention(intention, file, editor, intention.getText());
    try {
      if (changedDocument != null) {
        MadTestingUtil.restrictChangesToDocument(changedDocument, r);
      } else {
        r.run();
      }

      if (changedDocument != null && 
          PsiDocumentManager.getInstance(project).isDocumentBlockedByPsi(changedDocument)) {
        throw new AssertionError("Document is left blocked by PSI");
      }
      if (!hasErrors && textBefore != null && textBefore.equals(changedDocument.getText())) {
        throw new AssertionError("No change was performed in the document" +
                                 (intention.startInWriteAction() ? ".\nIf this fix doesn't change source files by design, it should return false from 'startInWriteAction'" : ""));
      }

      PsiTestUtil.checkPsiStructureWithCommit(getFile(), PsiTestUtil::checkStubsMatchText);
    }
    catch (Throwable error) {
      LOG.debug("Error occurred in " + this + ", text before:\n" + textBefore);
      throw error;
    }
  }

  @Nullable
  private Document getDocumentToBeChanged(IntentionAction intention) {
    PsiElement changedElement = intention.getElementToMakeWritable(getFile());
    PsiFile changedFile = changedElement == null ? null : changedElement.getContainingFile();
    return changedFile == null ? null : changedFile.getViewProvider().getDocument();
  }

  @Nullable
  private IntentionAction getRandomIntention(Editor editor, PsiFile file) {
    List<IntentionAction> actions = ContainerUtil.filter(CodeInsightTestFixtureImpl.getAvailableIntentions(editor, file), myPolicy::mayInvokeIntention);
    if (actions.isEmpty()) return null;
    
    // skip only after checking intentions for applicability, to catch possible exceptions from them
    int offset = editor.getCaretModel().getOffset();
    if (MadTestingUtil.isAfterError(file, offset) || MadTestingUtil.isAfterError(file, offset - 1)) {
      return null;
    }
    
    return actions.get(myIntentionIndex % actions.size());
  }
}
