// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.junit;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;


public class UniqueIdConfigurationProducer extends JUnitConfigurationProducer {

  protected UniqueIdConfigurationProducer() {
    super(JUnitConfigurationType.getInstance());
  }

  @Override
  protected boolean setupConfigurationFromContext(JUnitConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    String[] nodeIds = getNodeIds(context);
    if (nodeIds == null || nodeIds.length == 0) return false;
    final JUnitConfiguration.Data data = configuration.getPersistentData();
    data.setUniqueIds(nodeIds);
    data.TEST_OBJECT = JUnitConfiguration.TEST_UNIQUE_ID;
    configuration.setGeneratedName();
    setupConfigurationModule(context, configuration);
    return true;
  }

  public static String[] getNodeIds(ConfigurationContext context) {
    DataContext dataContext = context.getDataContext();
    AbstractTestProxy[] testProxies = dataContext.getData(AbstractTestProxy.DATA_KEYS);
    if (testProxies == null) return null;
    RunConfiguration runConfiguration = dataContext.getData(RunConfiguration.DATA_KEY);
    if (!(runConfiguration instanceof JUnitConfiguration)) return null;
    Module module = ((JUnitConfiguration)runConfiguration).getConfigurationModule().getModule();
    
    GlobalSearchScope searchScope =
      module != null ? GlobalSearchScope.moduleWithDependenciesScope(module) : GlobalSearchScope.projectScope(context.getProject());
    return 
      Arrays.stream(testProxies).map(testProxy -> TestUniqueId.getEffectiveNodeId(testProxy, context.getProject(), searchScope))
        .filter(Objects::nonNull)
        .toArray(String[]::new);
  }

  @Override
  protected boolean isApplicableTestType(String type, ConfigurationContext context) {
    return JUnitConfiguration.TEST_UNIQUE_ID.equals(type);
  }

  //prefer to method
  @Override
  public boolean shouldReplace(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return self.isProducedBy(UniqueIdConfigurationProducer.class) && (other.isProducedBy(TestInClassConfigurationProducer.class) || other.isProducedBy(PatternConfigurationProducer.class));
  }
}
