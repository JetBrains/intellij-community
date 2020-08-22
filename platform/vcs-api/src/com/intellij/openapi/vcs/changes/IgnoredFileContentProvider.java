// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Generates the content of the native (e.g. .gitignore, .hgignore) file based on file masks provided by {@link IgnoredFileProvider}.
 */
public interface IgnoredFileContentProvider {
  ExtensionPointName<IgnoredFileContentProvider> IGNORE_FILE_CONTENT_PROVIDER = new ExtensionPointName<>("com.intellij.ignoredFileContentProvider");

  @NotNull
  VcsKey getSupportedVcs();

  @NotNull
  @NlsSafe
  String getFileName();

  @NotNull
  @NonNls
  String buildIgnoreFileContent(@NotNull VirtualFile ignoreFileRoot, IgnoredFileProvider @NotNull [] ignoredFileProviders);

  @NotNull
  @NonNls
  String buildUnignoreContent(@NotNull @NonNls String ignorePattern);

  @NotNull
  @NonNls
  String buildIgnoreEntryContent(@NotNull VirtualFile ignoreEntryRoot, @NotNull IgnoredFileDescriptor ignoredFileDescriptor);

  @NotNull
  @NlsContexts.DetailedDescription
  String buildIgnoreGroupDescription(@NotNull IgnoredFileProvider ignoredFileProvider);

  default boolean supportIgnoreFileNotInVcsRoot() {
    return true;
  }

  default boolean canCreateIgnoreFileInStateStoreDir() {
    return true;
  }
}
