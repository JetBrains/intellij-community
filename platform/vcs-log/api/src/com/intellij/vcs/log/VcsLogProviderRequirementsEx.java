// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
   * Should load and refresh new refs.
   */
  boolean isRefreshRefs();

  /**
   * Returns the refs which were in the log before the refresh request.
   */
  @NotNull
  Collection<VcsRef> getPreviousRefs();
}
