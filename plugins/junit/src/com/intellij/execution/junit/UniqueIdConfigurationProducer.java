// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.DumbModeAccessType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

public class UniqueIdConfigurationProducer extends JUnitConfigurationProducer {
  @Override
  protected boolean setupConfigurationFromContext(@NotNull JUnitConfiguration configuration,
                                                  @NotNull ConfigurationContext context,
                                                  @NotNull Ref<PsiElement> sourceElement) {
    String[] nodeIds = getNodeIds(context);
    if (nodeIds == null || nodeIds.length == 0) return false;
    final JUnitConfiguration.Data data = configuration.getPersistentData();
    data.setUniqueIds(nodeIds);
    data.TEST_OBJECT = JUnitConfiguration.TEST_UNIQUE_ID;
    AbstractTestProxy selectedProxy = context.getDataContext().getData(AbstractTestProxy.DATA_KEY);
    if (selectedProxy != null) {
      configuration.setName(getGeneratedName(selectedProxy, configuration));
    }
    else {
      configuration.setGeneratedName();
    }
    setupConfigurationModule(context, configuration);
    return true;
  }
  
  private static String getGeneratedName(@NotNull AbstractTestProxy selectedProxy,
                                         @NotNull JUnitConfiguration configuration) {
    GlobalSearchScope searchScope = configuration.getSearchScope();
      if (searchScope != null) {
        Location<?> location = selectedProxy.getLocation(configuration.getProject(), searchScope);
        if (location instanceof MethodLocation) {
          StringJoiner stringJoiner = new StringJoiner(".");
          MethodLocation methodLocation = (MethodLocation)location;
          stringJoiner.add(methodLocation.getContainingClass().getName());
          String methodName = methodLocation.getPsiElement().getName();
          String proxyName = selectedProxy.getName();
          if (!proxyName.startsWith(methodName + "(")) {
            stringJoiner.add(methodName);
          }
          stringJoiner.add(proxyName);
          return stringJoiner.toString(); 
        }
      }
      return selectedProxy.getName();
  }

  @Override
  protected boolean isConfiguredByElement(@NotNull JUnitConfiguration configuration,
                                          @NotNull ConfigurationContext context,
                                          @NotNull PsiElement element) {
    return Arrays.equals(configuration.getPersistentData().getUniqueIds(), getNodeIds(context));
  }

  public static String[] getNodeIds(ConfigurationContext context) {
    DataContext dataContext = context.getDataContext();
    AbstractTestProxy[] testProxies = dataContext.getData(AbstractTestProxy.DATA_KEYS);
    if (testProxies == null) return null;
    RunConfiguration runConfiguration = dataContext.getData(RunConfiguration.DATA_KEY);
    if (!(runConfiguration instanceof JUnitConfiguration)) return null;
    Module module = ((JUnitConfiguration)runConfiguration).getConfigurationModule().getModule();

    Project project = context.getProject();
    GlobalSearchScope searchScope =
      module != null ? GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module) : GlobalSearchScope.projectScope(project);
    if (!DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> JUnitUtil.isJUnit5(searchScope, project) || 
                                                                    TestObject.hasJUnit5EnginesAPI(searchScope, JavaPsiFacade.getInstance(project)))) {
      return null;
    }
    return
      Arrays.stream(testProxies).map(testProxy -> TestUniqueId.getEffectiveNodeId(testProxy, project, searchScope))
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
