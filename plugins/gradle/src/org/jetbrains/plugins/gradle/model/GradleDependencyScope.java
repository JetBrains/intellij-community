/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.model;

import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 11/25/13
 */
public enum GradleDependencyScope {
  // Implicit scopes
  PROVIDED("provided", "provided", true, true, true, true),
  OPTIONAL("optional", "compile", true, true, true, true),


  // Java Plugin Scopes
  /**
   * Compile time dependencies
   */
  COMPILE("compile", "compile", true, true, true, true),
  /**
   * Runtime dependencies
   */
  RUNTIME("runtime", "runtime", false, true, false, true),
  /**
   * Additional dependencies for compiling tests.
   */
  TEST_COMPILE("testCompile", "test", false, false, true, true),
  /**
   * Additional dependencies for running tests only.
   */
  TEST_RUNTIME("testRuntime", "test", false, false, false, true),


  // War Plugin Scopes
  /**
   * the same scope as the compile scope dependencies, except that they are not added to the WAR archive.
   */
  PROVIDED_COMPILE("providedCompile", "provided", true, true, true, true),
  /**
   * the same scope as the runtime scope dependencies, except that they are not added to the WAR archive.
   */
  PROVIDED_RUNTIME("providedRuntime", "provided", false, true, false, true),

  // Groovy Plugin Scopes
  /**
   * Compiles production Groovy source files.
   */
  COMPILE_GROOVY("compileGroovy", "compile", true, true, true, true),
  /**
   * Compiles test Groovy source files.
   */
  COMPILE_TEST_GROOVY("compileTestGroovy", "test", false, false, true, true),

  // Scala Plugin Scopes
  /**
   * Compiles production Scala source files.
   */
  COMPILE_SCALA("compileScala", "compile", true, true, true, true),
  /**
   * Compiles test Scala source files.
   */
  COMPILE_TEST_SCALA("compileTestScala", "test", false, false, true, true);

  private final String myGradleName;
  private final String myIdeaMappingName;
  private final boolean myForProductionCompile;
  private final boolean myForProductionRuntime;
  private final boolean myForTestCompile;
  private final boolean myForTestRuntime;

  GradleDependencyScope(String gradleName,
                        String ideaMappingName,
                        boolean forProductionCompile,
                        boolean forProductionRuntime,
                        boolean forTestCompile,
                        boolean forTestRuntime) {
    myGradleName = gradleName;
    myIdeaMappingName = ideaMappingName;
    myForProductionCompile = forProductionCompile;
    myForProductionRuntime = forProductionRuntime;
    myForTestCompile = forTestCompile;
    myForTestRuntime = forTestRuntime;
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

  @Nullable
  public static GradleDependencyScope fromName(final String scopeName) {
    for (GradleDependencyScope scope : values()) {
      if (scope.myGradleName.equals(scopeName)) return scope;
    }
    return null;
  }

  public String getIdeaMappingName() {
    return myIdeaMappingName;
  }

  @Override
  public String toString() {
    return myGradleName;
  }
}
