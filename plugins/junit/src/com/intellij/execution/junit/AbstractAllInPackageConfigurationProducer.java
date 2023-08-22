// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.junit2.info.LocationUtil;
import com.intellij.execution.testframework.AbstractJavaTestConfigurationProducer;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractAllInPackageConfigurationProducer extends JUnitConfigurationProducer {
  /**
   * @deprecated Override {@link #getConfigurationFactory()}.
   */
  @Deprecated(forRemoval = true)
  protected AbstractAllInPackageConfigurationProducer(ConfigurationType configurationType) {
    super(configurationType);
  }

  protected AbstractAllInPackageConfigurationProducer() {
  }

  @Override
  protected boolean isApplicableTestType(String type, ConfigurationContext context) {
    return JUnitConfiguration.TEST_PACKAGE.equals(type);
  }

  @Override
  protected boolean setupConfigurationFromContext(@NotNull JUnitConfiguration configuration,
                                                  @NotNull ConfigurationContext context,
                                                  @NotNull Ref<PsiElement> sourceElement) {
    PsiPackage psiPackage = AbstractJavaTestConfigurationProducer.checkPackage(context.getPsiLocation());
    if (psiPackage == null) return false;
    sourceElement.set(psiPackage);
    if (!LocationUtil.isJarAttached(context.getLocation(), psiPackage, JUnitUtil.TEST_CASE_CLASS, JUnitUtil.TEST5_ANNOTATION,
                                    JUnitCommonClassNames.ORG_JUNIT_PLATFORM_ENGINE_TEST_ENGINE)) return false;
    final JUnitConfiguration.Data data = configuration.getPersistentData();
    data.PACKAGE_NAME = psiPackage.getQualifiedName();
    data.TEST_OBJECT = JUnitConfiguration.TEST_PACKAGE;
    data.setScope(setupPackageConfiguration(context, configuration, data.getScope()));
    configuration.setGeneratedName();
    return true;
  }

  @Override
  public boolean isPreferredConfiguration(ConfigurationFromContext self, ConfigurationFromContext other) {
    return !other.isProducedBy(AbstractAllInDirectoryConfigurationProducer.class) && !other.isProducedBy(PatternConfigurationProducer.class);
  }
}
