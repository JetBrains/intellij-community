// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NotNullFunction;
import com.intellij.util.PathsList;
import com.intellij.util.Processor;
import org.jetbrains.annotations.ApiStatus;
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
 */
@ApiStatus.NonExtendable
public abstract class OrderEnumerator {
  /**
   * Skip test dependencies
   *
   * @return this instance
   */
  public abstract @NotNull OrderEnumerator productionOnly();

  /**
   * Skip runtime-only dependencies
   *
   * @return this instance
   */
  public abstract @NotNull OrderEnumerator compileOnly();

  /**
   * Skip compile-only dependencies
   *
   * @return this instance
   */
  public abstract @NotNull OrderEnumerator runtimeOnly();

  public abstract @NotNull OrderEnumerator withoutSdk();

  public abstract @NotNull OrderEnumerator withoutLibraries();

  public abstract @NotNull OrderEnumerator withoutDepModules();

  /**
   * Skip root module's entries
   * @return this
   */
  public abstract @NotNull OrderEnumerator withoutModuleSourceEntries();

  public @NotNull OrderEnumerator librariesOnly() {
    return withoutSdk().withoutDepModules().withoutModuleSourceEntries();
  }

  public @NotNull OrderEnumerator sdkOnly() {
    return withoutDepModules().withoutLibraries().withoutModuleSourceEntries();
  }

  public VirtualFile @NotNull [] getAllLibrariesAndSdkClassesRoots() {
    return withoutModuleSourceEntries().withoutDepModules().recursively().exportedOnly().classes().usingCache().getRoots();
  }

  public VirtualFile @NotNull [] getAllSourceRoots() {
    return recursively().exportedOnly().sources().usingCache().getRoots();
  }

  /**
   * Recursively process modules on which the module depends. This flag is ignored for modules imported from Maven because for such modules
   * transitive dependencies are propagated to the root module during importing.
   *
   * @return this instance
   */
  public abstract @NotNull OrderEnumerator recursively();

  /**
   * Skip not exported dependencies. If this method is called after {@link #recursively()} direct non-exported dependencies won't be skipped
   *
   * @return this instance
   */
  public abstract @NotNull OrderEnumerator exportedOnly();

  /**
   * Process only entries which satisfies the specified condition
   *
   * @param condition filtering condition
   * @return this instance
   */
  public abstract @NotNull OrderEnumerator satisfying(@NotNull Condition<? super OrderEntry> condition);

  /**
   * Use {@code provider.getRootModel()} to process module dependencies
   *
   * @param provider provider
   * @return this instance
   */
  public abstract @NotNull OrderEnumerator using(@NotNull RootModelProvider provider);

  /**
   * Determine if, given the current enumerator settings and handlers for a module, should the
   * enumerator recurse to further modules based on the given ModuleOrderEntry?
   *
   * @param entry the ModuleOrderEntry in question (m1 -> m2)
   * @param handlers custom handlers registered to the module
   * @return true if the enumerator would have recursively processed the given ModuleOrderEntry.
   */
  public abstract boolean shouldRecurse(@NotNull ModuleOrderEntry entry, @NotNull List<? extends OrderEnumerationHandler> handlers);

  /**
   * @return {@link OrderRootsEnumerator} instance for processing classes roots
   */
  public abstract @NotNull OrderRootsEnumerator classes();

  /**
   * @return {@link OrderRootsEnumerator} instance for processing source roots
   */
  public abstract @NotNull OrderRootsEnumerator sources();

  /**
   * @param rootType root type
   * @return {@link OrderRootsEnumerator} instance for processing roots of the specified type
   */
  public abstract @NotNull OrderRootsEnumerator roots(@NotNull OrderRootType rootType);

  /**
   * @param rootTypeProvider custom root type provider
   * @return {@link OrderRootsEnumerator} instance for processing roots of the provided type
   */
  public abstract @NotNull OrderRootsEnumerator roots(@NotNull NotNullFunction<? super OrderEntry, ? extends OrderRootType> rootTypeProvider);

  /**
   * @return classes roots for all entries processed by this enumerator
   */
  public VirtualFile @NotNull [] getClassesRoots() {
    return classes().getRoots();
  }

  /**
   * @return source roots for all entries processed by this enumerator
   */
  public VirtualFile @NotNull [] getSourceRoots() {
    return sources().getRoots();
  }

  /**
   * @return list containing classes roots for all entries processed by this enumerator
   */
  public @NotNull PathsList getPathsList() {
    return classes().getPathsList();
  }

  /**
   * @return list containing source roots for all entries processed by this enumerator
   */
  public @NotNull PathsList getSourcePathsList() {
    return sources().getPathsList();
  }

  /**
   * Runs {@code processor.process()} for each entry processed by this enumerator.
   *
   * @param processor processor
   */
  public abstract void forEach(@NotNull Processor<? super OrderEntry> processor);

  /**
   * Runs {@code processor.process()} for each library processed by this enumerator.
   *
   * @param processor processor
   */
  public abstract void forEachLibrary(@NotNull Processor<? super Library> processor);

  /**
   * Runs {@code processor.process()} for each module processed by this enumerator.
   *
   * @param processor processor
   */
  public abstract void forEachModule(@NotNull Processor<? super Module> processor);

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
  public static @NotNull OrderEnumerator orderEntries(@NotNull Module module) {
    return ModuleRootManager.getInstance(module).orderEntries();
  }

  /**
   * Creates new enumerator instance to process dependencies of all modules in {@code project}. Only first level dependencies of
   * modules are processed so {@link #recursively()} option is ignored and {@link #withoutDepModules()} option is forced
   *
   * @param project project
   * @return new enumerator instance
   */
  public static @NotNull OrderEnumerator orderEntries(@NotNull Project project) {
    return ProjectRootManager.getInstance(project).orderEntries();
  }
}