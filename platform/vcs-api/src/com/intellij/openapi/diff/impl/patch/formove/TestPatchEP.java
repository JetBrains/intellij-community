/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.patch.formove;

import com.intellij.openapi.diff.impl.patch.PatchEP;
import com.intellij.openapi.vcs.changes.CommitContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author irengrig
 *         Date: 7/12/11
 *         Time: 1:15 PM
 */
public class TestPatchEP implements PatchEP {
  private final static String ourName = "com.intellij.openapi.diff.impl.patch.formove.TestPatchEP";
  private final static String ourContent = "ourContent\nseveral\nlines\twith\u0142\u0001 different symbols";

  @NotNull
  @Override
  public String getName() {
    return ourName;
  }

  @Override
  public CharSequence provideContent(@NotNull String path, CommitContext commitContext) {
    return ourContent + path;
  }

  @Override
  public void consumeContent(@NotNull String path, @NotNull CharSequence content, CommitContext commitContext) {
    assert (ourContent + path).equals(content.toString());
  }

  @Override
  public void consumeContentBeforePatchApplied(@NotNull String path,
                                               @NotNull CharSequence content,
                                               CommitContext commitContext) {
  }
}
