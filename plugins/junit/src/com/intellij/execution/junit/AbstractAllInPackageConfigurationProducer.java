 // Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.junit2.info.LocationUtil;
import com.intellij.java.library.JavaLibraryUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public abstract class AbstractAllInPackageConfigurationProducer extends JUnitConfigurationProducer {
  private static final String mavenJunitPlatformEngine = "org.junit.platform:junit-platform-engine";
  private static final String mavenJunit = "junit:junit";
  private static final String mavenJunitJupiterApi = "org.junit.jupiter:junit-jupiter-api";

  protected AbstractAllInPackageConfigurationProducer() {
  }

  @Override
  protected boolean isApplicableTestType(String type, ConfigurationContext context) {
    return JUnitConfiguration.TEST_PACKAGE.equals(type);
  }

  @Override
  public boolean setupConfigurationFromContext(@NotNull JUnitConfiguration configuration,
                                                  @NotNull ConfigurationContext context,
                                                  @NotNull Ref<PsiElement> sourceElement) {
    Location<PsiElement> loc = context.getLocation();
    if (loc == null) return false;
    PsiElement psiElement = loc.getPsiElement();
    PsiPackage psiPackage = checkPackage(psiElement);
    if (psiPackage == null) return false;
    sourceElement.set(psiPackage);

    final JUnitConfiguration.Data data = configuration.getPersistentData();
    if (DumbService.isDumb(psiElement.getProject())) {
      if (!hasProjectLibraryAttached(psiPackage)) return false;
    } else {
      if (psiElement instanceof PsiDirectory psiDirectory && !hasLibraryAttached(loc, psiDirectory)) return false;
      if (psiElement instanceof PsiPackage && !hasLibraryAttached(loc, psiPackage)) return false;
    }

    data.PACKAGE_NAME = psiPackage.getQualifiedName();
    data.TEST_OBJECT = JUnitConfiguration.TEST_PACKAGE;
    data.setScope(setupPackageConfiguration(context, configuration, data.getScope()));
    configuration.setGeneratedName();
    return true;
  }

  private static boolean hasProjectLibraryAttached(PsiPackage psiPackage) {
    return JavaLibraryUtil.hasAnyLibraryJar(
      psiPackage.getProject(),
      Set.of(mavenJunitPlatformEngine, mavenJunit, mavenJunitJupiterApi)
    );
  }

  private static boolean hasLibraryAttached(Location<PsiElement> loc, PsiDirectory directory) {
    return LocationUtil.isJarAttached(loc, new PsiDirectory[]{directory},
                                      JUnitUtil.TEST_CASE_CLASS,
                                      JUnitUtil.TEST5_ANNOTATION,
                                      JUnitCommonClassNames.ORG_JUNIT_PLATFORM_ENGINE_TEST_ENGINE);
  }


  private static boolean hasLibraryAttached(Location<PsiElement> loc, PsiPackage pkg) {
    return LocationUtil.isJarAttached(loc, pkg,
                                      JUnitUtil.TEST_CASE_CLASS,
                                      JUnitUtil.TEST5_ANNOTATION,
                                      JUnitCommonClassNames.ORG_JUNIT_PLATFORM_ENGINE_TEST_ENGINE);
  }

  @Override
  public boolean isPreferredConfiguration(ConfigurationFromContext self, ConfigurationFromContext other) {
    return !other.isProducedBy(AbstractAllInDirectoryConfigurationProducer.class) &&
           !other.isProducedBy(PatternConfigurationProducer.class);
  }
}
