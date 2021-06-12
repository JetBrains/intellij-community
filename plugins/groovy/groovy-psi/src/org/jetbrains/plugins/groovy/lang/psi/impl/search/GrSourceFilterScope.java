// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.search;

import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;

public final class GrSourceFilterScope extends DelegatingGlobalSearchScope {
  private @Nullable final ProjectFileIndex myIndex;

  public GrSourceFilterScope(@NotNull final GlobalSearchScope delegate) {
    super(delegate, "groovy.sourceFilter");
    myIndex = getProject() == null ? null : ProjectFileIndex.getInstance(getProject());
  }

  @Override
  public boolean contains(@NotNull final VirtualFile file) {
    return super.contains(file) && (myIndex == null || myIndex.isInSourceContent(file)) && FileTypeRegistry.getInstance().isFileOfType(file, GroovyFileType.GROOVY_FILE_TYPE);
  }
}
