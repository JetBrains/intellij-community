// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NotNullFunction;
import com.intellij.util.PathsList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * Interface for processing roots of OrderEntry's from {@link OrderEnumerator}.
 *
 * @see OrderEnumerator#classes()
 * @see OrderEnumerator#sources()
 */
@ApiStatus.NonExtendable
public interface OrderRootsEnumerator {
  /**
   * @return all roots processed by this enumerator
   */
  VirtualFile @NotNull [] getRoots();

  /**
   * todo IJPL-339 mark experimental
   * @return all roots processed by this enumerator
   */
  @ApiStatus.Internal
  @NotNull @Unmodifiable
  Collection<RootEntry> getRootEntries();

  /**
   * @return urls of all roots processed by this enumerator
   */
  String @NotNull [] getUrls();

  /**
   * @return list of path to all roots processed by this enumerator
   */
  @NotNull
  PathsList getPathsList();

  /**
   * Add all source roots processed by this enumerator to {@code list}
   * @param list list
   */
  void collectPaths(@NotNull PathsList list);

  /**
   * If roots for this enumerator are already evaluated the cached result will be used. Otherwise roots will be evaluated and cached for
   * subsequent calls. <p>
   * Caching is not supported if {@link OrderEnumerator#satisfying}, {@link OrderEnumerator#using} or {@link #usingCustomSdkRootProvider}
   * option is used
   * @return this instance
   */
  @NotNull
  OrderRootsEnumerator usingCache();

  /**
   * This method makes sense only when dependencies of a module are processed (i.e. the enumerator instance is obtained by using {@link OrderEnumerator#orderEntries(com.intellij.openapi.module.Module)} or
   * {@link ModuleRootModel#orderEntries()}). It instructs the enumerator to skip the output of the main module (if {@link OrderEnumerator#productionOnly()}
   * option is not specified then only the test output will be skipped)
   *
   * @return this instance
   */
  @NotNull
  OrderRootsEnumerator withoutSelfModuleOutput();

  /**
   * Instructs the enumerator to use {@code provider} to obtain roots of an SDK order entry instead of taking them from SDK configuration. 
   * Note that this option won't affect the result of {@link #getUrls()} method
   * 
   * @return this instance
   */
  @NotNull
  OrderRootsEnumerator usingCustomSdkRootProvider(@NotNull NotNullFunction<? super JdkOrderEntry, VirtualFile[]> provider);
}
