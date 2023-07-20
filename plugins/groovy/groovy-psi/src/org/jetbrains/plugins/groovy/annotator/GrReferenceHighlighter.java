// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.impl.BackgroundUpdateHighlightersUtil;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;

import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.plugins.groovy.annotator.GrReferenceHighlighterFactory.shouldHighlight;

/**
 * @author Max Medvedev
 */
public class GrReferenceHighlighter extends TextEditorHighlightingPass {
  private final PsiFile myPsiFile;
  private final GroovyFileBase myGroovyBaseFile;

  GrReferenceHighlighter(@NotNull PsiFile psiFile, @NotNull GroovyFileBase groovyBaseFile, @NotNull Document document) {
    super(groovyBaseFile.getProject(), document);
    myPsiFile = psiFile;
    myGroovyBaseFile = groovyBaseFile;
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    if (!shouldHighlight(myGroovyBaseFile)) return;
    List<HighlightInfo> myInfos = new ArrayList<>();
    myGroovyBaseFile.accept(new InaccessibleElementVisitor(myGroovyBaseFile, myProject, (__, info) -> myInfos.add(info)));
    BackgroundUpdateHighlightersUtil.setHighlightersToEditor(myProject, myPsiFile, myDocument, 0, myGroovyBaseFile.getTextLength(), myInfos, getId());
  }

  @Override
  public void doApplyInformationToEditor() {
  }
}
