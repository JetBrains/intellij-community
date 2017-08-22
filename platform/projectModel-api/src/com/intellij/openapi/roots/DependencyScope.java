/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

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
 * In order to check whether a dependency should be included in a classpath use one of {@code isFor}
 * methods instead of direct comparison with the enum constants
 *
 * @author yole
 */
public enum DependencyScope {
  COMPILE("Compile", true, true, true, true),
  TEST("Test", false, false, true, true),
  RUNTIME("Runtime", false, true, false, true),
  PROVIDED("Provided", true, false, true, true);
  private final String myDisplayName;
  private final boolean myForProductionCompile;
  private final boolean myForProductionRuntime;
  private final boolean myForTestCompile;
  private final boolean myForTestRuntime;

  public static final String SCOPE_ATTR = "scope";

  DependencyScope(String displayName,
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

  public String getDisplayName() {
    return myDisplayName;
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
  public String toString() {
    return myDisplayName;
  }
}
