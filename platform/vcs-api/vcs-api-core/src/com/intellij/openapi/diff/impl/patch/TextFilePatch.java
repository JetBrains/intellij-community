/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.patch;

import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class TextFilePatch extends FilePatch {
  private Charset myCharset;
  private final List<PatchHunk> myHunks;

  public TextFilePatch(@Nullable Charset charset) {
    myCharset = charset;
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
  }

  public void addHunk(final PatchHunk hunk) {
    myHunks.add(hunk);
  }

  public List<PatchHunk> getHunks() {
    return Collections.unmodifiableList(myHunks);
  }

  @Override
  public boolean isNewFile() {
    return myHunks.size() == 1 && myHunks.get(0).isNewContent();
  }

  public String getNewFileText() {
    return myHunks.get(0).getText();
  }

  @Override
  public boolean isDeletedFile() {
    return myHunks.size() == 1 && myHunks.get(0).isDeletedContent();
  }

  @Nullable
  public Charset getCharset() {
    return myCharset;
  }
}
