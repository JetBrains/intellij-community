// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.roots;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.projectModel.ProjectModelBundle;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * The table below specifies which order entries are used during compilation and runtime.
 * <table border=1>
 * <thead><td></td><td>Production<br/>Compile</td><td>Production<br/>Runtime</td>
 * <td>Test<br/>Compile</td><td>Test<br/>Runtime</td></thead>
 * <tbody>
 * <tr><td>{@link #COMPILE}</td>      <td>*</td><td>*</td><td>*</td><td>*</td></tr>
 * <tr><td>{@link #TEST}</td>         <td> </td><td> </td><td>*</td><td>*</td></tr>
 * <tr><td>{@link #RUNTIME}</td>      <td> </td><td>*</td><td> </td><td>*</td></tr>
 * <tr><td>{@link #PROVIDED}</td>     <td>*</td><td> </td><td>*</td><td>*</td></tr>
 * <tr><td>Production<br/>Output</td> <td> </td><td>*</td><td>*</td><td>*</td></tr>
 * <tr><td>Test<br/>Output</td>       <td> </td><td> </td><td> </td><td>*</td></tr>
 * </tbody>
 * </table>
 * <br>
 * 
 * Note that the way dependencies are processed may be changed by plugins if the project is imported from a build system. So values from
 * this enum are supposed to be used only to edit dependencies (via {@link ExportableOrderEntry#setScope}). If you need to determine which
 * dependencies are included into a classpath, use {@link OrderEnumerator}.
 */
public enum DependencyScope {
  COMPILE(ProjectModelBundle.messagePointer("dependency.scope.compile"), true, true, true, true),
  TEST(ProjectModelBundle.messagePointer("dependency.scope.test"), false, false, true, true),
  RUNTIME(ProjectModelBundle.messagePointer("dependency.scope.runtime"), false, true, false, true),
  PROVIDED(ProjectModelBundle.messagePointer("dependency.scope.provided"), true, false, true, true);
  private final @NotNull Supplier<@NlsContexts.ListItem String> myDisplayName;
  private final boolean myForProductionCompile;
  private final boolean myForProductionRuntime;
  private final boolean myForTestCompile;
  private final boolean myForTestRuntime;

  public static final @NonNls String SCOPE_ATTR = "scope";

  DependencyScope(@NotNull Supplier<@NlsContexts.ListItem String> displayName,
                  boolean forProductionCompile,
                  boolean forProductionRuntime,
                  boolean forTestCompile,
                  boolean forTestRuntime) {
    myDisplayName = displayName;
    myForProductionCompile = forProductionCompile;
    myForProductionRuntime = forProductionRuntime;
    myForTestCompile = forTestCompile;
    myForTestRuntime = forTestRuntime;
  }

  /**
   * Returns a scope that covers every use-case that is covered by at least one of the given scopes. If multiple such scopes exist, this
   * method will return the narrowest of them, i.e. the one that covers the least use-cases.
   *
   * @param scope1 The first scope.
   * @param scope2 The second scope.
   * @return The narrowest scope that covers every use-case that is covered by at least one of the given scopes.
   * @see #coversUseCasesOf(DependencyScope)
   */
  public static @NotNull DependencyScope coveringUseCasesOf(@NotNull DependencyScope scope1, @NotNull DependencyScope scope2) {
    if (scope1.coversUseCasesOf(scope2)) return scope1;
    if (scope2.coversUseCasesOf(scope1)) return scope2;
    // At this point, we know that `scope1` and `scope2` are different, neither is COMPILE, and they're not PROVIDED+TEST either.
    // The remaining cases are:
    // - PROVIDED+RUNTIME = COMPILE
    // - TEST+RUNTIME = COMPILE (wider than necessary, but there's no better alternative)
    return COMPILE;
  }

  public static @NotNull DependencyScope readExternal(@NotNull Element element) {
    String scope = element.getAttributeValue(SCOPE_ATTR);
    if (scope != null) {
      try {
        return valueOf(scope);
      }
      catch (IllegalArgumentException e) {
        return COMPILE;
      }
    }
    else {
      return COMPILE;
    }
  }

  public void writeExternal(Element element) {
    if (this != COMPILE) {
      element.setAttribute(SCOPE_ATTR, name());
    }
  }

  public @NotNull
  @NlsContexts.ListItem String getDisplayName() {
    return myDisplayName.get();
  }

  /**
   * Tells whether a dependency with this scope will be used in <em>Production Compile</em> use-cases or not.
   */
  public boolean isForProductionCompile() {
    return myForProductionCompile;
  }

  /**
   * Tells whether a dependency with this scope will be used in <em>Production Runtime</em> use-cases or not.
   */
  public boolean isForProductionRuntime() {
    return myForProductionRuntime;
  }

  /**
   * Tells whether a dependency with this scope will be used in <em>Test Compile</em> use-cases or not.
   */
  public boolean isForTestCompile() {
    return myForTestCompile;
  }

  /**
   * Tells whether a dependency with this scope will be used in <em>Test Runtime</em> use-cases or not.
   */
  public boolean isForTestRuntime() {
    return myForTestRuntime;
  }

  /**
   * <p>Tells whether this scope covers every use-case covered by the given other scope (and potentially more) or not.</p>
   *
   * <p>The result of this method will be {@code true} if and only if the set of use-cases covered by this scope include every use-case
   * covered by the other one. This scope may cover use-cases that the other scope does not, but not the other way around. To be exact:</p>
   * <ul>
   * <li>If {@code that.}{@link #isForProductionCompile()} is {@code true}, then {@code this.}{@link #isForProductionCompile()} must also be
   * {@code true}.</li>
   * <li>If {@code that.}{@link #isForProductionRuntime()} is {@code true}, then {@code this.}{@link #isForProductionRuntime()} must also be
   * {@code true}.</li>
   * <li>If {@code that.}{@link #isForTestCompile()} is {@code true}, then {@code this.}{@link #isForTestCompile()} must also be
   * {@code true}.</li>
   * <li>If {@code that.}{@link #isForTestRuntime()} is {@code true}, then {@code this.}{@link #isForTestRuntime()} must also be
   * {@code true}.</li>
   * </ul>
   *
   * @param that The other scope.
   * @return {@code true} if {@code this} scope covers every use-case covered by {@code that} scope, {@code false} otherwise.
   */
  public boolean coversUseCasesOf(@NotNull DependencyScope that) {
    if (this == that) return true;
    // Check every use-case whether there's any that's covered by `that` but not `this`
    boolean thatCoversUseCaseNotCoveredByThis =
      (that.myForProductionCompile && !this.myForProductionCompile) ||
      (that.myForProductionRuntime && !this.myForProductionRuntime) ||
      (that.myForTestCompile && !this.myForTestCompile) ||
      (that.myForTestRuntime && !this.myForTestRuntime);
    // Return `true` if there's no such use-case
    return !thatCoversUseCaseNotCoveredByThis;
  }

  @Override
  public @NlsContexts.ListItem String toString() {
    return getDisplayName();
  }
}
