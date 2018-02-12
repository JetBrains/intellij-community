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
package com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.CvsCommandOperation;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.Nullable;

/**
 * author: lesya
 */
public abstract class TagsProviderOnEnvironment implements TagsProvider {

  @Override
  @Nullable
  public CvsCommandOperation getOperation() {
    final CvsEnvironment env = getCvsEnvironment();
    if (env == null) return null;
    return new GetAllBranchesOperation(env, getModule());
  }

  @Nullable
  protected abstract CvsEnvironment getCvsEnvironment();

  public String getModule() {
    return ".";
  }
}
