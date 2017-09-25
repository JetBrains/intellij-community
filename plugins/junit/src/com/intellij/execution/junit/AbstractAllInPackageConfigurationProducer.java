/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.junit;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.junit2.info.LocationUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.siyeh.ig.junit.JUnitCommonClassNames;


public abstract class AbstractAllInPackageConfigurationProducer extends JUnitConfigurationProducer {

  protected AbstractAllInPackageConfigurationProducer(ConfigurationType configurationType) {
    super(configurationType);
  }

  @Override
  protected boolean setupConfigurationFromContext(JUnitConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    PsiPackage psiPackage = JavaRuntimeConfigurationProducerBase.checkPackage(context.getPsiLocation());
    if (psiPackage == null) return false;
    sourceElement.set(psiPackage);
    if (!LocationUtil.isJarAttached(context.getLocation(), psiPackage, JUnitUtil.TESTCASE_CLASS, JUnitUtil.TEST5_ANNOTATION,
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
