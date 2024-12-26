// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.junit;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class TestMethods extends TestMethod {
  private static final Logger LOG = Logger.getInstance(TestMethods.class);

  private final Collection<? extends AbstractTestProxy> myFailedTests;

  public TestMethods(@NotNull JUnitConfiguration configuration,
                     @NotNull ExecutionEnvironment environment,
                     @NotNull Collection<? extends AbstractTestProxy> failedTests) {
    super(configuration, environment);

    myFailedTests = failedTests;
  }

  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    final JavaParameters javaParameters = super.createDefaultJavaParameters();
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    final RunConfigurationModule configurationModule = getConfiguration().getConfigurationModule();
    final Project project = configurationModule.getProject();
    final Module module = configurationModule.getModule();
    final GlobalSearchScope searchScope = module != null ? module.getModuleRuntimeScope(true)
                                                         : GlobalSearchScope.allScope(project);
    ReadAction.run(() -> addClassesListToJavaParameters(myFailedTests, testInfo -> testInfo != null
                                                                                   ? getTestPresentation(testInfo, project, searchScope)
                                                                                   : null, data.getPackageName(), true, javaParameters));

    return javaParameters;
  }

  @Override
  protected PsiElement retrievePsiElement(Object element) {
    if (element instanceof SMTestProxy) {
      JUnitConfiguration configuration = getConfiguration();
      Location location = ((SMTestProxy)element).getLocation(configuration.getProject(),
                                                             configuration.getConfigurationModule().getSearchScope());
      if (location != null) {
        return location.getPsiElement();
      }
    }
    return super.retrievePsiElement(element);
  }

  @Override
  public @Nullable SourceScope getSourceScope() {
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    return data.getScope().getSourceScope(getConfiguration());
  }

  @Override
  protected boolean configureByModule(Module module) {
    return super.configureByModule(module) && getConfiguration().getPersistentData().getScope() != TestSearchScope.WHOLE_PROJECT;
  }

  public static @Nullable String getTestPresentation(AbstractTestProxy testInfo, Project project, GlobalSearchScope searchScope) {
    final Location location = testInfo.getLocation(project, searchScope);
    final PsiElement element = location != null ? location.getPsiElement() : null;
    if (element instanceof PsiMethod) {
      String nodeId = TestUniqueId.getEffectiveNodeId(testInfo, project, searchScope);
      if (nodeId != null) {
        return TestUniqueId.getUniqueIdPresentation().fun(nodeId);
      }

      final PsiClass containingClass = location instanceof MethodLocation ? ((MethodLocation)location).getContainingClass()
                                                                          : location instanceof PsiMemberParameterizedLocation ? ((PsiMemberParameterizedLocation)location).getContainingClass() 
                                                                                                                               : ((PsiMethod)element).getContainingClass();
      if (containingClass != null) {
        final String proxyName = testInfo.getName();
        final String methodWithSignaturePresentation = JUnitConfiguration.Data.getMethodPresentation(((PsiMethod)element));
        return JavaExecutionUtil.getRuntimeQualifiedName(containingClass) + "," +
               (proxyName.contains(methodWithSignaturePresentation) ? proxyName.substring(proxyName.indexOf(methodWithSignaturePresentation)) : methodWithSignaturePresentation);
      }
    }
    return null;
  }

  @Override
  public String suggestActionName() {
    return ActionsBundle.message("action.RerunFailedTests.text");
  }
}
