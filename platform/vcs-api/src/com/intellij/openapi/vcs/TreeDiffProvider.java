// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;

public interface TreeDiffProvider {
  Collection<String> getRemotelyChanged(final VirtualFile vcsRoot, final Collection<String> paths);
}
