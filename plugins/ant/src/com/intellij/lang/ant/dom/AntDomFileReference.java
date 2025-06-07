// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.dom;

import com.intellij.lang.ant.AntBundle;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AntDomFileReference extends FileReference implements AntDomReference{

  private boolean mySkipByAnnotator;

  public AntDomFileReference(final AntDomFileReferenceSet set, final TextRange range, final int index, final String text) {
    super(set, range, index, text);
  }

  @Override
  public @Nullable @NlsSafe String getText() {
    final AntDomFileReferenceSet refSet = getFileReferenceSet();
    final String _path = AntStringResolver.computeString(refSet.getAttributeValue(), super.getText());
    final String text = FileUtil.toSystemIndependentName(_path);
    return text.endsWith("/")? text.substring(0, text.length() - "/".length()) : text;
  }

  @Override
  public @NotNull AntDomFileReferenceSet getFileReferenceSet() {
    return (AntDomFileReferenceSet)super.getFileReferenceSet();
  }

  @Override
  public @NotNull String getUnresolvedMessagePattern() {
    return AntBundle.message("file.doesnt.exist", getCanonicalRepresentationText());
  }

  public @Nullable String getCanonicalRepresentationText() {
    final String value = getCanonicalText();
    return AntStringResolver.computeString(getFileReferenceSet().getAttributeValue(), value);
  }

  @Override
  public boolean shouldBeSkippedByAnnotator() {
    return mySkipByAnnotator;
  }

  @Override
  public void setShouldBeSkippedByAnnotator(boolean value) {
    mySkipByAnnotator = value;
  }
}