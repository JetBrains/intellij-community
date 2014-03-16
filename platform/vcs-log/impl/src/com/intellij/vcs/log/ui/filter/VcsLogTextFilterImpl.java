/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.filter;

import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsLogTextFilter;
import com.intellij.vcs.log.VcsLogDetailsFilter;
import org.jetbrains.annotations.NotNull;

public class VcsLogTextFilterImpl implements VcsLogDetailsFilter, VcsLogTextFilter {

  @NotNull private final String myText;

  public VcsLogTextFilterImpl(@NotNull String text) {
    myText = text;
  }

  @Override
  public boolean matches(@NotNull VcsCommitMetadata details) {
    return details.getFullMessage().toLowerCase().contains(myText.toLowerCase());
  }

  @Override
  @NotNull
  public String getText() {
    return myText;
  }
}
