/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.junit.codeInsight;

import com.intellij.codeInsight.daemon.quickFix.ExternalLibraryResolver;
import com.intellij.execution.junit.JUnitExternalLibraryDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class JUnit5ExternalLibraryResolver extends ExternalLibraryResolver {
  private static final Set<String> JUNIT5_ANNOTATIONS = ContainerUtil.set(
    "Test", "Disabled", "TestFactory", "BeforeEach", "BeforeAll", "AfterEach", "AfterAll", "DisplayName", "Nested"
  );
  @Nullable
  @Override
  public ExternalClassResolveResult resolveClass(@NotNull String shortClassName, @NotNull ThreeState isAnnotation, @NotNull Module contextModule) {
    if (isAnnotation == ThreeState.YES && JUNIT5_ANNOTATIONS.contains(shortClassName)) {
      return new ExternalClassResolveResult("org.junit.jupiter.api." + shortClassName, JUnitExternalLibraryDescriptor.JUNIT5);
    }
    return null;
  }

  @Nullable
  @Override
  public ExternalLibraryDescriptor resolvePackage(@NotNull String packageName) {
    if (packageName.equals("org.junit.jupiter") || packageName.equals("org.junit")) {
      return JUnitExternalLibraryDescriptor.JUNIT5;
    }
    return null;
  }
}
