// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.vcs.FileStatus;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TextFilePatch extends FilePatch {
  private final Charset myCharset;
  private final @Nullable String myLineSeparator;
  private final List<PatchHunk> myHunks;
  private @Nullable FileStatus myFileStatus;

  public TextFilePatch(@Nullable Charset charset) {
    this(charset, null);
  }

  public TextFilePatch(@Nullable Charset charset, @Nullable String lineSeparator) {
    myCharset = charset;
    myLineSeparator = lineSeparator;
    myHunks = new ArrayList<>();
  }

  public TextFilePatch pathsOnlyCopy() {
    return new TextFilePatch(this);
  }

  private TextFilePatch(final TextFilePatch patch) {
    myCharset = patch.myCharset;
    setBeforeVersionId(patch.getBeforeVersionId());
    setAfterVersionId(patch.getAfterVersionId());
    setBeforeName(patch.getBeforeName());
    setAfterName(patch.getAfterName());
    myHunks = patch.myHunks;
    myLineSeparator = patch.getLineSeparator();
    setNewFileMode(patch.getNewFileMode());
    setFileStatus(patch.myFileStatus);
  }

  public void addHunk(final PatchHunk hunk) {
    myHunks.add(hunk);
  }

  public List<PatchHunk> getHunks() {
    return Collections.unmodifiableList(myHunks);
  }

  public boolean hasNoModifiedContent() {
    return myHunks.isEmpty();
  }

  @Override
  public boolean isNewFile() {
    return myFileStatus == FileStatus.ADDED || (myHunks.size() == 1 && myHunks.get(0).isNewContent());
  }

  public String getSingleHunkPatchText() {
    if (myHunks.isEmpty()) return "";     // file can be empty, only status changed
    assert myHunks.size() == 1;
    return myHunks.get(0).getText();
  }

  @Override
  public boolean isDeletedFile() {
    return myFileStatus == FileStatus.DELETED || (myHunks.size() == 1 && myHunks.get(0).isDeletedContent());
  }

  public @Nullable Charset getCharset() {
    return myCharset;
  }

  public @Nullable String getLineSeparator() {
    return myLineSeparator;
  }

  public void setFileStatus(@Nullable FileStatus fileStatus) {
    myFileStatus = fileStatus;
  }
}
