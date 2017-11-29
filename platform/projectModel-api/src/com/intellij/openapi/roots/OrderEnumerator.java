/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NotNullFunction;
import com.intellij.util.PathsList;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * <p>Interface for convenient processing dependencies of a module or a project. Allows to process {@link OrderEntry}s
 * and collect classes and source roots.</p>
 *
 * <p>Use {@link #orderEntries(Module)} or {@link ModuleRootModel#orderEntries()} to process dependencies of a module
 * and use {@link #orderEntries(Project)} to process dependencies of all modules in a project.</p>
 *
 * <p>Note that all configuration methods modify {@link OrderEnumerator} instance instead of creating a new one.</p>
 *
 * @author nik
 * @since 10.0
 */
public abstract class OrderEnumerator {
  /**
   * Skip test dependencies
   *
   * @return this instance
   */
  public abstract OrderEnumerator productionOnly();

  /**
   * Skip runtime-only dependencies
   *
   * @return this instance
   */
  public abstract OrderEnumerator compileOnly();

  /**
   * Skip compile-only dependencies
   *
   * @return this instance
   */
  public abstract OrderEnumerator runtimeOnly();

  public abstract OrderEnumerator withoutSdk();

  public abstract OrderEnumerator withoutLibraries();

  public abstract OrderEnumerator withoutDepModules();

  /**
   * Skip root module's entries
   * @return this
   */
  public abstract OrderEnumerator withoutModuleSourceEntries();

  public OrderEnumerator librariesOnly() {
    return withoutSdk().withoutDepModules().withoutModuleSourceEntries();
  }

  public OrderEnumerator sdkOnly() {
    return withoutDepModules().withoutLibraries().withoutModuleSourceEntries();
  }

  public VirtualFile[] getAllLibrariesAndSdkClassesRoots() {
    return withoutModuleSourceEntries().withoutDepModules().recursively().exportedOnly().classes().usingCache().getRoots();
  }

  public VirtualFile[] getAllSourceRoots() {
    return recursively().exportedOnly().sources().usingCache().getRoots();
  }

  /**
   * Recursively process modules on which the module depends. This flag is ignored for modules imported from Maven because for such modules
   * transitive dependencies are propagated to the root module during importing.
   *
   * @return this instance
   */
  public abstract OrderEnumerator recursively();

  /**
   * Skip not exported dependencies. If this method is called after {@link #recursively()} direct non-exported dependencies won't be skipped
   *
   * @return this instance
   */
  public abstract OrderEnumerator exportedOnly();

  /**
   * Process only entries which satisfies the specified condition
   *
   * @param condition filtering condition
   * @return this instance
   */
  public abstract OrderEnumerator satisfying(Condition<OrderEntry> condition);

  /**
   * Use {@code provider.getRootModel()} to process module dependencies
   *
   * @param provider provider
   * @return this instance
   */
  public abstract OrderEnumerator using(@NotNull RootModelProvider provider);

  /**
   * Determine if, given the current enumerator settings and handlers for a module, should the
   * enumerator recurse to further modules based on the given ModuleOrderEntry?
   *
   * @param entry the ModuleOrderEntry in question (m1 -> m2)
   * @param handlers custom handlers registered to the module
   * @return true if the enumerator would have recursively processed the given ModuleOrderEntry.
   */
  public abstract boolean shouldRecurse(@NotNull ModuleOrderEntry entry, @NotNull List<OrderEnumerationHandler> handlers);

  /**
   * @return {@link OrderRootsEnumerator} instance for processing classes roots
   */
  public abstract OrderRootsEnumerator classes();

  /**
   * @return {@link OrderRootsEnumerator} instance for processing source roots
   */
  public abstract OrderRootsEnumerator sources();

  /**
   * @param rootType root type
   * @return {@link OrderRootsEnumerator} instance for processing roots of the specified type
   */
  public abstract OrderRootsEnumerator roots(@NotNull OrderRootType rootType);

  /**
   * @param rootTypeProvider custom root type provider
   * @return {@link OrderRootsEnumerator} instance for processing roots of the provided type
   */
  public abstract OrderRootsEnumerator roots(@NotNull NotNullFunction<OrderEntry, OrderRootType> rootTypeProvider);

  /**
   * @return classes roots for all entries processed by this enumerator
   */
  public VirtualFile[] getClassesRoots() {
    return classes().getRoots();
  }

  /**
   * @return source roots for all entries processed by this enumerator
   */
  public VirtualFile[] getSourceRoots() {
    return sources().getRoots();
  }

  /**
   * @return list containing classes roots for all entries processed by this enumerator
   */
  public PathsList getPathsList() {
    return classes().getPathsList();
  }

  /**
   * @return list containing source roots for all entries processed by this enumerator
   */
  public PathsList getSourcePathsList() {
    return sources().getPathsList();
  }

  /**
   * Runs {@code processor.process()} for each entry processed by this enumerator.
   *
   * @param processor processor
   */
  public abstract void forEach(@NotNull Processor<OrderEntry> processor);

  /**
   * Runs {@code processor.process()} for each library processed by this enumerator.
   *
   * @param processor processor
   */
  public abstract void forEachLibrary(@NotNull Processor<Library> processor);

  /**
   * Runs {@code processor.process()} for each module processed by this enumerator.
   *
   * @param processor processor
   */
  public abstract void forEachModule(@NotNull Processor<Module> processor);

  /**
   * Passes order entries to the specified visitor.
   *
   * @param policy       the visitor to accept.
   * @param initialValue the default value to be returned by the visit process.
   * @return the value returned by the visitor.
   * @see OrderEntry#accept(RootPolicy, Object)
   */
  public abstract <R> R process(@NotNull RootPolicy<R> policy, R initialValue);

  /**
   * Creates new enumerator instance to process dependencies of {@code module}
   *
   * @param module module
   * @return new enumerator instance
   */
  @NotNull
  public static OrderEnumerator orderEntries(@NotNull Module module) {
    return ModuleRootManager.getInstance(module).orderEntries();
  }

  /**
   * Creates new enumerator instance to process dependencies of all modules in {@code project}. Only first level dependencies of
   * modules are processed so {@link #recursively()} option is ignored and {@link #withoutDepModules()} option is forced
   *
   * @param project project
   * @return new enumerator instance
   */
  @NotNull
  public static OrderEnumerator orderEntries(@NotNull Project project) {
    return ProjectRootManager.getInstance(project).orderEntries();
  }
}