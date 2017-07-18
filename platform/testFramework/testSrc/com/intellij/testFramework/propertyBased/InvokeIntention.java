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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import slowCheck.Generator;

import java.util.List;

public class InvokeIntention extends ActionOnRange {
  private final PsiFile myFile;
  private final int myIntentionIndex;
  private final IntentionPolicy myPolicy;
  private final String myConstructorArgs;
  private String myInvocationLog = "not invoked";

  InvokeIntention(PsiFile file, int offset, int intentionIndex, IntentionPolicy policy) {
    super(file.getViewProvider().getDocument(), offset, offset);
    myFile = file;
    myIntentionIndex = intentionIndex;
    myPolicy = policy;
    myConstructorArgs = "_ , " + offset + ", " + intentionIndex + ", _";
  }

  @NotNull
  public static Generator<InvokeIntention> randomIntentions(@NotNull PsiFile psiFile, @NotNull IntentionPolicy policy) {
    return Generator.zipWith(Generator.integers(0, psiFile.getTextLength()), Generator.integers(0, 100),
                             (offset, index) -> new InvokeIntention(psiFile, offset, index, policy)).noShrink();
  }

  @Override
  public String toString() {
    return "InvokeIntention(" + myConstructorArgs + "){" + myFile.getVirtualFile().getPath() + "," + myInvocationLog + "}";
  }

  public void performAction() {
    int offset = getStartOffset();
    myInvocationLog = "offset " + offset;
    if (offset < 0) return;

    Editor editor = FileEditorManager.getInstance(myFile.getProject()).openTextEditor(new OpenFileDescriptor(myFile.getProject(), myFile.getVirtualFile(), offset), true);
    
    List<HighlightInfo> infos = RehighlightAllEditors.highlightEditor(editor, myFile.getProject());
    boolean hasErrors = infos.stream().anyMatch(i -> i.getSeverity() == HighlightSeverity.ERROR);

    IntentionAction intention = getRandomIntention(editor);
    if (intention == null) return;
    myInvocationLog += ", invoke '" + intention.getText() + "'";

    Document changedDocument = getDocumentToBeChanged(intention);
    String textBefore = changedDocument == null ? null : changedDocument.getText();

    Runnable r = () -> CodeInsightTestFixtureImpl.invokeIntention(intention, myFile, editor, intention.getText());
    if (changedDocument != null) {
      MadTestingUtil.restrictChangesToDocument(changedDocument, r);
    } else {
      r.run();
    }

    if (changedDocument != null && 
        PsiDocumentManager.getInstance(myFile.getProject()).isDocumentBlockedByPsi(changedDocument)) {
      throw new AssertionError("Document is left blocked by PSI");
    }
    if (!hasErrors && textBefore != null && textBefore.equals(changedDocument.getText())) {
      throw new AssertionError("No change was performed in the document");
    }

    PsiTestUtil.checkPsiStructureWithCommit(myFile, PsiTestUtil::checkStubsMatchText);
  }

  @Nullable
  private Document getDocumentToBeChanged(IntentionAction intention) {
    PsiElement changedElement = intention.getElementToMakeWritable(myFile);
    PsiFile changedFile = changedElement == null ? null : changedElement.getContainingFile();
    return changedFile == null ? null : changedFile.getViewProvider().getDocument();
  }

  @Nullable
  private IntentionAction getRandomIntention(Editor editor) {
    List<IntentionAction> actions = ContainerUtil.filter(CodeInsightTestFixtureImpl.getAvailableIntentions(editor, myFile),
                                                         myPolicy::mayInvokeIntention);
    return actions.isEmpty() ? null : actions.get(myIntentionIndex % actions.size());
  }
}
