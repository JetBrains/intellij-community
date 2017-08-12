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
import jetCheck.Generator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    return "InvokeIntention{" + getVirtualFile().getPath() + ", " + myInvocationLog + ", raw=(" + myInitialStart + "," + myIntentionIndex + ")}";
  }

  public void performAction() {
    int offset = getFinalStartOffset();
    myInvocationLog = "offset " + offset;
    if (offset < 0) return;

    Project project = getProject();
    Editor editor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, getVirtualFile(), offset), true);

    boolean hasErrors = !highlightErrors(project, editor).isEmpty();

    PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, getProject());
    IntentionAction intention = getRandomIntention(editor, file);
    if (intention == null) {
      myInvocationLog += ", no intentions found after highlighting";
      return;
    }
    myInvocationLog += ", invoked '" + intention.getText() + "'";
    String intentionString = intention.toString();

    boolean mayBreakCode = myPolicy.mayBreakCode(intention, editor, file);
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
        String message = "No change was performed in the document";
        if (intention.startInWriteAction()) {
          message += ".\nIf it's by design that " + intentionString + " doesn't change source files, " +
                     "it should return false from 'startInWriteAction'";
        }
        throw new AssertionError(message);
      }

      PsiTestUtil.checkPsiStructureWithCommit(getFile(), PsiTestUtil::checkStubsMatchText);

      if (!mayBreakCode && !hasErrors) {
        checkNoNewErrors(project, editor, intentionString);
      }
    }
    catch (Throwable error) {
      LOG.debug("Error occurred in " + this + ", text before:\n" + textBefore);
      throw error;
    }
  }

  private static void checkNoNewErrors(Project project, Editor editor, String intentionString) {
    List<HighlightInfo> errors = highlightErrors(project, editor);
    if (!errors.isEmpty()) {
      throw new AssertionError("New highlighting errors introduced after invoking " + intentionString + 
                               "\nIf this is correct, add it to IntentionPolicy#mayBreakCode." +
                               "\nErrors found: " + errors);
    }
  }

  @NotNull
  private static List<HighlightInfo> highlightErrors(Project project, Editor editor) {
    List<HighlightInfo> infos = RehighlightAllEditors.highlightEditor(editor, project);
    return ContainerUtil.filter(infos, i -> i.getSeverity() == HighlightSeverity.ERROR);
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
