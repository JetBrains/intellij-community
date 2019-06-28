// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Generates the content of the native (e.g. .gitignore, .hgignore) file based on file masks provided by {@link IgnoredFileProvider}.
 */
public interface IgnoredFileContentProvider {
  ExtensionPointName<IgnoredFileContentProvider> IGNORE_FILE_CONTENT_PROVIDER = ExtensionPointName.create("com.intellij.ignoredFileContentProvider");

  @NotNull
  VcsKey getSupportedVcs();

  @NotNull
  String getFileName();

  @NotNull
  String buildIgnoreFileContent(@NotNull VirtualFile ignoreFileRoot, @NotNull IgnoredFileProvider[] ignoredFileProviders);

  @NotNull
  String buildUnignoreContent(@NotNull String ignorePattern);

  @NotNull
  String buildIgnoreEntryContent(@NotNull VirtualFile ignoreFileRoot, @NotNull IgnoredFileDescriptor ignoredFileDescriptor);

  @NotNull
  String buildIgnoreGroupDescription(@NotNull IgnoredFileProvider ignoredFileProvider);

  default boolean supportIgnoreFileNotInVcsRoot() {
    return true;
  }
}
