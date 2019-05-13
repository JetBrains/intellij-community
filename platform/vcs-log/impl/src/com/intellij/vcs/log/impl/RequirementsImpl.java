/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log.impl;

import com.intellij.vcs.log.VcsLogProviderRequirementsEx;
import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class RequirementsImpl implements VcsLogProviderRequirementsEx {

  private final int myCommitCount;
  private final boolean myRefresh;
  @NotNull private final Collection<VcsRef> myPreviousRefs;

  public RequirementsImpl(int count, boolean refresh, @NotNull Collection<VcsRef> previousRefs) {
    myCommitCount = count;
    myRefresh = refresh;
    myPreviousRefs = previousRefs;
  }

  @Override
  public int getCommitCount() {
    return myCommitCount;
  }

  @Override
  public boolean isRefresh() {
    return myRefresh;
  }

  @NotNull
  @Override
  public Collection<VcsRef> getPreviousRefs() {
    return myPreviousRefs;
  }
}
