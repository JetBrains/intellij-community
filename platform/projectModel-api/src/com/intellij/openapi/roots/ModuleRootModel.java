/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.List;
import java.util.Set;

/**
 * Interface providing root information model for a given module.
 * It's implemented by {@link ModuleRootManager}.
 */
@ApiStatus.NonExtendable
public interface ModuleRootModel {
  /**
   * Returns the module to which the model belongs.
   *
   * @return the module instance.
   */
  @NotNull
  Module getModule();

  /**
   * Use this method to obtain all content entries of a module. Entries are given in
   * lexicographical order of their paths.
   *
   * @return array of content entries for this module
   * @see ContentEntry
   */
  ContentEntry @NotNull [] getContentEntries();

  /**
   * Use this method to obtain order of roots of a module. Order of entries is important.
   *
   * @return array of order entries for this module
   */
  OrderEntry @NotNull [] getOrderEntries();

  /**
   * Returns the SDK used by the module.
   *
   * @return either module-specific or inherited SDK
   * @see #isSdkInherited()
   */
  @Nullable
  Sdk getSdk();

  /**
   * Returns {@code true} if SDK for this module is inherited from a project.
   *
   * @return true if the SDK is inherited, false otherwise
   * @see ProjectRootManager#getProjectSdk()
   * @see ProjectRootManager#setProjectSdk(Sdk)
   */
  boolean isSdkInherited();

  /**
   * Returns an array of content roots from all content entries.
   *
   * @return the array of content roots.
   * @see #getContentEntries()
   */
  VirtualFile @NotNull [] getContentRoots();

  /**
   * Returns an array of content root urls from all content entries.
   *
   * @return the array of content root URLs.
   * @see #getContentEntries()
   */
  String @NotNull [] getContentRootUrls();

  /**
   * Returns an array of exclude roots from all content entries.
   *
   * @return the array of excluded roots.
   * @see #getContentEntries()
   */
  VirtualFile @NotNull [] getExcludeRoots();

  /**
   * Returns an array of exclude root urls from all content entries.
   *
   * @return the array of excluded root URLs.
   * @see #getContentEntries()
   */
  String @NotNull [] getExcludeRootUrls();

  /**
   * Returns an array consisting of source roots of all types from all content entries. 
   * If you're interested in a specific type of source root, use {@link #getSourceRoots(JpsModuleSourceRootType)} instead.  
   *
   * @return the array of source roots.
   * @see #getContentEntries()
   * @see #getSourceRoots(boolean)
   */
  @NotNull VirtualFile @NotNull [] getSourceRoots();

  /**
   * Returns an array of source roots from all content entries.
   * If {@code includingTests} is {@code true}, this is equivalent to {@link #getSourceRoots()}. 
   * Otherwise, the returned array doesn't contain roots of types designated for tests (e.g., Java test source roots and Java test resource
   * roots).
   *
   * @param includingTests determines whether test source roots should be included in the result
   * @return the array of source roots.
   * @see #getContentEntries()
   */
  VirtualFile @NotNull [] getSourceRoots(boolean includingTests);

  /**
   * Return a list of source roots of the specified type.
   *
   * @param rootType type of source roots
   * @return list of source roots
   */
  @NotNull
  @Unmodifiable
  List<VirtualFile> getSourceRoots(@NotNull JpsModuleSourceRootType<?> rootType);

  /**
   * Return a list of source roots which types belong to the specified set.
   *
   * @param rootTypes types of source roots
   * @return list of source roots
   */
  @NotNull
  @Unmodifiable List<VirtualFile> getSourceRoots(@NotNull Set<? extends JpsModuleSourceRootType<?>> rootTypes);

  /**
   * Returns an array consisting of source roots of all types from all content entries.
   * The result includes URLs of all files returned by {@link #getSourceRoots()}, and also includes URLs of non-existing source roots.
   *
   * @return the array of source root URLs.
   * @see #getContentEntries()
   * @see #getSourceRootUrls(boolean)
   */
  String @NotNull [] getSourceRootUrls();

  /**
   * Returns an array of source root urls from all content entries, with optionally excluded test roots.
   * The result includes URLs of all files returned by {@link #getSourceRoots(boolean)}, and also includes URLs of non-existing source 
   * roots.
   *
   * @param includingTests determines whether test source root urls should be included in the result
   * @return the array of source root URLs.
   * @see #getContentEntries()
   */
  String @NotNull [] getSourceRootUrls(boolean includingTests);

  /**
   * Passes all order entries in the module to the specified visitor.
   *
   * @param policy       the visitor to accept.
   * @param initialValue the default value to be returned by the visit process.
   * @return the value returned by the visitor.
   * @see OrderEntry#accept(RootPolicy, Object)
   */
  <R> R processOrder(@NotNull RootPolicy<R> policy, R initialValue);

  /**
   * Returns {@link OrderEnumerator} instance which can be used to process order entries of the module (with or without dependencies) and
   * collect classes or source roots.
   *
   * @return {@link OrderEnumerator} instance
   */
  @NotNull
  OrderEnumerator orderEntries();

  /**
   * Returns list of module names <i>this module</i> depends on.
   *
   * @return the array of module names this module depends on.
   */
  String @NotNull [] getDependencyModuleNames();

  <T> T getModuleExtension(@NotNull Class<T> klass);

  Module @NotNull [] getModuleDependencies();

  Module @NotNull [] getModuleDependencies(boolean includeTests);
}
