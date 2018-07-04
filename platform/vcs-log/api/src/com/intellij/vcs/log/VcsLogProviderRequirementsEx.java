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
package com.intellij.vcs.log;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.vcs.log.VcsLogProvider.Requirements;

/**
 * Extension of the standard {@link Requirements} which contains data used by some VCSs. <br/>
 * An instance of this class is actually passed to {@link VcsLogProvider#readFirstBlock(VirtualFile, Requirements)},
 * but VcsLogProviders which need this additional information must check for instanceof before casting & be able to fallback.
 */
public interface VcsLogProviderRequirementsEx extends Requirements {

  /**
   * Tells if this request is made during log initialization, or during refresh
   * Returns true if it is refresh; false if it is initialization.
   */
  boolean isRefresh();

  /**
   * Returns the refs which were in the log before the refresh request.
   */
  @NotNull
  Collection<VcsRef> getPreviousRefs();
}
