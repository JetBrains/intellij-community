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
package com.intellij.execution.junit.codeInsight;

import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.codeInsight.daemon.quickFix.ExternalLibraryResolver;
import com.intellij.execution.junit.JUnit3Framework;
import com.intellij.execution.junit.JUnit4Framework;
import com.intellij.openapi.module.Module;
import com.intellij.testIntegration.JavaTestFramework;
import com.intellij.testIntegration.TestFramework;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public class JUnitExternalLibraryResolver extends ExternalLibraryResolver {
  public static final ExternalLibraryDescriptor JUNIT3 = createJUnitDescriptor("3", JUnit3Framework.class);
  public static final ExternalLibraryDescriptor JUNIT4 = createJUnitDescriptor("4", JUnit4Framework.class);

  @NotNull
  private static ExternalLibraryDescriptor createJUnitDescriptor(final String version, final Class<? extends JavaTestFramework> frameworkClass) {
    return new ExternalLibraryDescriptor("junit", "junit", version) {
      @NotNull
      @Override
      public List<String> getLibraryClassesRoots() {
        return TestFramework.EXTENSION_NAME.findExtension(frameworkClass).getLibraryPaths();
      }

      @Override
      public String getPresentableName() {
        return "JUnit" + version;
      }
    };
  }

  private static Set<String> JUNIT4_ANNOTATIONS = ContainerUtil.set(
    "Test", "Ignore", "RunWith", "Before", "BeforeClass", "After", "AfterClass"
  );
  @Nullable
  @Override
  public ExternalClassResolveResult resolveClass(@NotNull String shortClassName, @NotNull ThreeState isAnnotation, @NotNull Module contextModule) {
    if ("TestCase".equals(shortClassName)) {
      return new ExternalClassResolveResult("junit.framework.TestCase", JUNIT3);
    }
    if (isAnnotation == ThreeState.YES && JUNIT4_ANNOTATIONS.contains(shortClassName)) {
      return new ExternalClassResolveResult("org.junit." + shortClassName, JUNIT4);
    }
    return null;
  }

  @Nullable
  @Override
  public ExternalLibraryDescriptor resolvePackage(@NotNull String packageName) {
    if (packageName.equals("org.junit")) {
      return JUNIT4;
    }
    if (packageName.equals("junit.framework")) {
      return JUNIT3;
    }
    return null;
  }
}
