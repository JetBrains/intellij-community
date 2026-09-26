// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * A {@link SyntheticLibrary} that holds JVM class files or JVM sources.
 * <p>
 * Use this class, and not {@link SyntheticLibrary} itself, when a JVM language must resolve against the library.
 * Only the roots of a {@code JavaSyntheticLibrary} get a JVM package prefix in the workspace file index.
 * As a result, {@code PackageIndex} reports a package name only for these roots.
 * {@code JavaPsiFacade.findPackage} also finds a package only for these roots.
 * <p>
 * {@code PackageIndexTest} holds both halves of this contract.
 * The test {@code synthetic java library} shows the packages of this class.
 * The test {@code synthetic non-java library} shows no package for a plain {@link SyntheticLibrary}.
 *
 * @see AdditionalLibraryRootsProvider
 */
public class JavaSyntheticLibrary extends ImmutableSyntheticLibrary {

  public JavaSyntheticLibrary(@NotNull String comparisonId,
                              @NotNull List<? extends VirtualFile> sourceRoots,
                              @NotNull List<? extends VirtualFile> binaryRoots,
                              @NotNull Set<? extends VirtualFile> excludedRoots) {
    super(comparisonId, sourceRoots, binaryRoots, excludedRoots, null, null);
  }
}
