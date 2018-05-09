/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.execution.testframework.AbstractInClassConfigurationProducer;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class TestInClassConfigurationProducer extends JUnitConfigurationProducer {
  private final JUnitInClassConfigurationProducerDelegate myDelegate = new JUnitInClassConfigurationProducerDelegate();
  public TestInClassConfigurationProducer() {
    super(JUnitConfigurationType.getInstance());
  }

  @Override
  protected boolean setupConfigurationFromContext(JUnitConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    return myDelegate.setupConfigurationFromContext(configuration, context, sourceElement);
  }

  @Override
  public void onFirstRun(@NotNull ConfigurationFromContext configuration,
                         @NotNull ConfigurationContext fromContext,
                         @NotNull Runnable performRunnable) {
    myDelegate.onFirstRun(configuration, fromContext, performRunnable);
  }

  @Override
  public boolean isConfigurationFromContext(JUnitConfiguration configuration, ConfigurationContext context) {
    String[] nodeIds = UniqueIdConfigurationProducer.getNodeIds(context);
    if (nodeIds != null && nodeIds.length > 0) return false;
    return super.isConfigurationFromContext(configuration, context);
  }

  @Override
  protected boolean isApplicableTestType(String type, ConfigurationContext context) {
    return myDelegate.isApplicableTestType(type, context);
  }

  private static class JUnitInClassConfigurationProducerDelegate
    extends AbstractInClassConfigurationProducer<JUnitConfiguration> {
    public JUnitInClassConfigurationProducerDelegate() {super(JUnitConfigurationType.getInstance());}

    @Override
    protected boolean isApplicableTestType(String type, ConfigurationContext context) {
      return JUnitConfiguration.TEST_CLASS.equals(type) || JUnitConfiguration.TEST_METHOD.equals(type);
    }

    @Override
    protected boolean setupConfigurationFromContext(JUnitConfiguration configuration, ConfigurationContext context, Ref<PsiElement> sourceElement) {
      return super.setupConfigurationFromContext(configuration, context, sourceElement);
    }
  }
}
