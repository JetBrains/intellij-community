/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.annotator;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;

import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.plugins.groovy.annotator.GrReferenceHighlighterFactory.shouldHighlight;

/**
 * @author Max Medvedev
 */
public class GrReferenceHighlighter extends TextEditorHighlightingPass {
  private final GroovyFileBase myFile;
  private List<HighlightInfo> myInfos = null;

  public GrReferenceHighlighter(@Nullable Document document, @NotNull GroovyFileBase file) {
    super(file.getProject(), document);
    myFile = file;
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    if (!shouldHighlight(myFile)) return;
    myInfos = new ArrayList<>();

    myFile.accept(new ResolveHighlightingVisitor(myFile, myProject, (e, info) -> myInfos.add(info)));
    myFile.accept(new InaccessibleElementVisitor(myFile, myProject, (e, info) -> myInfos.add(info)));
  }

  @Override
  public void doApplyInformationToEditor() {
    if (myInfos == null || myDocument == null) return;
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, myFile.getTextLength(), myInfos, getColorsScheme(), getId());
  }
}
