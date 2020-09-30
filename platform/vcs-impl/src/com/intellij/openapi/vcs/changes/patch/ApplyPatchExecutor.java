// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * @author irengrig
 */
public interface ApplyPatchExecutor<T extends AbstractFilePatchInProgress<?>> {
  @Nls(capitalization = Nls.Capitalization.Title)
  String getName();

  void apply(@NotNull List<? extends FilePatch> remaining,
             @NotNull MultiMap<VirtualFile, T> patchGroupsToApply,
             @Nullable LocalChangeList localList,
             @Nullable String fileName,
             @Nullable ThrowableComputable<Map<String, Map<String, CharSequence>>, PatchSyntaxException> additionalInfo);
}
