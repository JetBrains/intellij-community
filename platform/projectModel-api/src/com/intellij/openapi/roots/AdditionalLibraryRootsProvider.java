// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * Implement this EP to provide additional library roots. It can be useful when it's undesirable to create
 * {@link com.intellij.openapi.roots.libraries.Library} and attach it to a module via {@link OrderEntry}.
 * Additional library roots will extend {@link com.intellij.psi.search.GlobalSearchScope#allScope(Project)}
 * (in UI, "Project and Libraries" scope). Also, files contained in the roots will be shown as library files
 * in Project View and will be available in "Navigate | File..." popup.
 *
 * @see AdditionalLibraryRootsListener
 */
public abstract class AdditionalLibraryRootsProvider {
  public static final ExtensionPointName<AdditionalLibraryRootsProvider> EP_NAME = ExtensionPointName.create("com.intellij.additionalLibraryRootsProvider");

  /**
   * Returns a collection of {@link SyntheticLibrary}.
   * This method is suitable when it's easier to collect all additional library roots associated with {@code Project},
   * instead of {@code Module}. E.g. JavaScript libraries can be associated with files or folders allowing more
   * fine-grained control.
   *
   * @param project Project instance
   * @return a collection of {@link SyntheticLibrary}
   */
  public @NotNull Collection<SyntheticLibrary> getAdditionalProjectLibraries(@NotNull Project project) {
    return Collections.emptyList();
  }

  /**
   * The method returns roots that IDE should use to track external changes.
   * If the provider retrieves libraries that have mutable source roots, it makes sense return them as Watched Roots as well.
   *
   * Essentially, the method is a shortcut for {@link com.intellij.openapi.roots.WatchedRootsProvider}.
   *
   * CAUTION!
   * Each root provided by this method makes VFS update slower.
   * Please, avoid returning a lot of watched roots, especially if they have complicated internal structure.
   *
   * @param project Project instance
   * @see com.intellij.openapi.roots.WatchedRootsProvider
   */
  public @NotNull Collection<VirtualFile> getRootsToWatch(@NotNull Project project) {
    return Collections.emptyList();
  }
}
