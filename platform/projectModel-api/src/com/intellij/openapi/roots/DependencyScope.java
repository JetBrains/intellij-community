// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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

  @NotNull
  public static DependencyScope readExternal(@NotNull Element element) {
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

  @NotNull
  public @NlsContexts.ListItem String getDisplayName() {
    return myDisplayName.get();
  }

  public boolean isForProductionCompile() {
    return myForProductionCompile;
  }

  public boolean isForProductionRuntime() {
    return myForProductionRuntime;
  }

  public boolean isForTestCompile() {
    return myForTestCompile;
  }

  public boolean isForTestRuntime() {
    return myForTestRuntime;
  }

  @Override
  public @NlsContexts.ListItem String toString() {
    return getDisplayName();
  }
}
