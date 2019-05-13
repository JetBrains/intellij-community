/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.util.NotNullFunction;
import com.intellij.util.PathsList;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for processing roots of OrderEntry's from {@link OrderEnumerator}.
 *
 * @see OrderEnumerator#classes()
 * @see OrderEnumerator#sources()
 *
 * @author nik
 */
public interface OrderRootsEnumerator {
  /**
   * @return all roots processed by this enumerator
   */
  @NotNull
  VirtualFile[] getRoots();

  /**
   * @return urls of all roots processed by this enumerator
   */
  @NotNull
  String[] getUrls();

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
   * Caching is not supported if {@link OrderEnumerator#satisfying}, {@link OrderEnumerator#using} or {@link #usingCustomRootProvider}
   * option is used
   * @return this instance
   */
  @NotNull
  OrderRootsEnumerator usingCache();

  /**
   * This method makes sense only when dependencies of a module are processed (i.e. the enumerator instance is obtained by using {@link OrderEnumerator#orderEntries(com.intellij.openapi.module.Module)} or
   * {@link ModuleRootModel#orderEntries()}). It instructs the enumerator to skip the output of the main module (if {@link com.intellij.openapi.roots.OrderEnumerator#productionOnly()}
   * option is not specified then only the test output will be skipped)
   *
   * @return this instance
   */
  @NotNull
  OrderRootsEnumerator withoutSelfModuleOutput();

  /**
   * Use {@code provider} to obtain roots of an library or jdk order entry instead of {@link OrderEntry#getFiles(OrderRootType)} method. Note that
   * this option won't affect result of {@link #getUrls()} method
   * @param provider function to evaluate roots for an order entry
   * @return this instance
   */
  @NotNull
  OrderRootsEnumerator usingCustomRootProvider(@NotNull NotNullFunction<OrderEntry, VirtualFile[]> provider);
}
