// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.execution.configurations.coverage.JavaCoverageEnabledConfiguration;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.classFilter.ClassFilter;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CoverageExtendConfigTest extends CoverageIntegrationBaseTest {
  @Test
  public void testDirectoryScopeOnlyInstrumentsOwnClasses() {
    Module module = ModuleManager.getInstance(getProject()).findModuleByName("simple");

    // Create a run configuration for the module with no class/package specified —
    // this is the state when the user right-clicks the testSrc root directory and runs coverage.
    ApplicationConfiguration runConfig = new ApplicationConfiguration("", getProject(), ApplicationConfigurationType.getInstance());
    runConfig.setModule(module);

    // Simulate "create run config from context" — this calls extendCreatedConfiguration on all
    // registered extensions, including the coverage one, just as the IDE does at runtime.
    ReadAction.run(() -> {
      VirtualFile testSrcVFile = ModuleRootManager.getInstance(module).getContentRoots()[0].findChild("testSrc");
      Location<?> location = new PsiLocation<>(getProject(), PsiManager.getInstance(getProject()).findDirectory(testSrcVFile));
      JavaRunConfigurationExtensionManager.getInstance().extendCreatedConfiguration(runConfig, location);
    });

    ClassFilter[] patterns = JavaCoverageEnabledConfiguration.getFrom(runConfig).getCoveragePatterns();
    Assert.assertNotNull("Coverage patterns must be set for root-directory scope to avoid instrumenting all classes", patterns);

    // Classes compiled into out/test/simple/foo/ must be covered.
    Assert.assertTrue("foo.FooTest must be instrumented", isInstrumented("foo.FooTest", patterns));
    Assert.assertTrue("foo.bar.BarTest must be instrumented", isInstrumented("foo.bar.BarTest", patterns));

    // Third-party library classes must NOT be covered.
    Assert.assertFalse("org.apache.poi.Cell must not be instrumented", isInstrumented("org.apache.poi.Cell", patterns));
    Assert.assertFalse("org.mockito.Mockito must not be instrumented", isInstrumented("org.mockito.Mockito", patterns));
  }

  private static boolean isInstrumented(String className, ClassFilter[] patterns) {
    for (ClassFilter filter : patterns) {
      if (!filter.isEnabled()) continue;
      String pattern = filter.getPattern()
        .replace(".", "\\.")
        .replace("*", ".*");
      if (className.matches(pattern)) return true;
    }
    return false;
  }
}
