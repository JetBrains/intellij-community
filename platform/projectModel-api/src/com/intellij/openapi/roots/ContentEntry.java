/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.roots;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.List;
import java.util.Set;

/**
 * Represents a module's content root.
 * You can get existing entries with {@link com.intellij.openapi.roots.ModuleRootModel#getContentEntries()} or
 * create a new one with {@link ModifiableRootModel#addContentEntry(com.intellij.openapi.vfs.VirtualFile)}.
 *
 * @author dsl
 * @see ModuleRootModel#getContentEntries()
 * @see ModifiableRootModel#addContentEntry(com.intellij.openapi.vfs.VirtualFile)
 */
public interface ContentEntry extends Synthetic {
  /**
   * Returns the root file or directory for the content root, if it is valid.
   *
   * @return the content root file or directory, or null if content entry is invalid.
   */
  @Nullable
  VirtualFile getFile();

  /**
   * Returns the URL of content root.
   * To validate returned roots, use
   * <code>{@link com.intellij.openapi.vfs.VirtualFileManager#findFileByUrl(String)}</code>
   *
   * @return URL of content root, that should never be null.
   */
  @NotNull
  String getUrl();

  /**
   * Returns the list of source roots under this content root.
   *
   * @return list of this {@code ContentEntry} {@link com.intellij.openapi.roots.SourceFolder}s
   */
  @NotNull
  SourceFolder[] getSourceFolders();

  /**
   * @param rootType type of accepted source roots
   * @return list of source roots of the specified type containing in this content root
   */
  @NotNull
  List<SourceFolder> getSourceFolders(@NotNull JpsModuleSourceRootType<?> rootType);

  /**
   *
   * @param rootTypes types of accepted source roots
   * @return list of source roots of the specified types containing in this content root
   */
  @NotNull
  List<SourceFolder> getSourceFolders(@NotNull Set<? extends JpsModuleSourceRootType<?>> rootTypes);

  /**
   * Returns the list of files and directories for valid source roots under this content root.
   *
   * @return list of all valid source roots.
   */
  @NotNull
  VirtualFile[] getSourceFolderFiles();

  /**
   * Returns the list of excluded roots configured under this content root. The result doesn't include synthetic excludes like the module output.
   *
   * @return list of this {@code ContentEntry} {@link com.intellij.openapi.roots.ExcludeFolder}s
   */
  @NotNull
  ExcludeFolder[] getExcludeFolders();

  /**
   * @return list of URLs for all excluded roots under this content root including synthetic excludes like the module output
   */
  @NotNull
  List<String> getExcludeFolderUrls();

  /**
   * Returns the list of files and directories for valid excluded roots under this content root.
   *
   * @return list of all valid exclude roots including synthetic excludes like the module output
   */
  @NotNull
  VirtualFile[] getExcludeFolderFiles();

  /**
   * Adds a source or test source root under the content root.
   *
   * @param file         the file or directory to add as a source root.
   * @param isTestSource true if the file or directory is added as a test source root.
   * @return the object representing the added root.
   */
  @NotNull
  SourceFolder addSourceFolder(@NotNull VirtualFile file, boolean isTestSource);

  /**
   * Adds a source or test source root with the specified package prefix under the content root.
   *
   * @param file          the file or directory to add as a source root.
   * @param isTestSource  true if the file or directory is added as a test source root.
   * @param packagePrefix the package prefix for the root to add, or an empty string if no
   *                      package prefix is required.
   * @return the object representing the added root.
   */
  @NotNull
  SourceFolder addSourceFolder(@NotNull VirtualFile file, boolean isTestSource, @NotNull String packagePrefix);

  @NotNull
  <P extends JpsElement>
  SourceFolder addSourceFolder(@NotNull VirtualFile file, @NotNull JpsModuleSourceRootType<P> type, @NotNull P properties);

  @NotNull
  <P extends JpsElement>
  SourceFolder addSourceFolder(@NotNull VirtualFile file, @NotNull JpsModuleSourceRootType<P> type);

  /**
   * Adds a source or test source root under the content root.
   *
   * @param  url the file or directory url to add as a source root.
   * @param isTestSource true if the file or directory is added as a test source root.
   * @return the object representing the added root.
   */
  @NotNull
  SourceFolder addSourceFolder(@NotNull String url, boolean isTestSource);

  @NotNull
  <P extends JpsElement>
  SourceFolder addSourceFolder(@NotNull String url, @NotNull JpsModuleSourceRootType<P> type);

  @NotNull
  <P extends JpsElement>
  SourceFolder addSourceFolder(@NotNull String url, @NotNull JpsModuleSourceRootType<P> type, @NotNull  P properties);

  /**
   * Removes a source or test source root from this content root.
   *
   * @param sourceFolder the source root to remove (must belong to this content root).
   */
  void removeSourceFolder(@NotNull SourceFolder sourceFolder);

  void clearSourceFolders();

  /**
   * Adds an exclude root under the content root.
   *
   * @param file the file or directory to add as an exclude root.
   * @return the object representing the added root.
   */
  ExcludeFolder addExcludeFolder(@NotNull VirtualFile file);

  /**
   * Adds an exclude root under the content root.
   *
   * @param url the file or directory url to add as an exclude root.
   * @return the object representing the added root.
   */
  ExcludeFolder addExcludeFolder(@NotNull String url);

  /**
   * Removes an exclude root from this content root.
   *
   * @param excludeFolder the exclude root to remove (must belong to this content root).
   */
  void removeExcludeFolder(@NotNull ExcludeFolder excludeFolder);

  /**
   * Removes an exclude root from this content root.
   * @param url url of the exclude root
   * @return {@code true} if the exclude root was removed
   */
  boolean removeExcludeFolder(@NotNull String url);

  void clearExcludeFolders();

  /**
   * Returns patterns for names of files which should be excluded from this content root. If name of a file under this content root matches
   * any of the patterns it'll be excluded from the module, if name of a directory matches any of the patterns the directory and all of its
   * contents will be excluded. '?' and '*' wildcards are supported.
   */
  @NotNull
  List<String> getExcludePatterns();

  void addExcludePattern(@NotNull String pattern);
  void removeExcludePattern(@NotNull String pattern);
  void setExcludePatterns(@NotNull List<String> patterns);
}
