// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.junit;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.execution.testframework.AbstractPatternBasedConfigurationProducer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

public class PatternConfigurationProducer extends AbstractPatternBasedConfigurationProducer<JUnitConfiguration> {
  public PatternConfigurationProducer() {
    super(JUnitConfigurationType.getInstance());
  }

  @Override
  protected String getMethodPresentation(PsiMember psiMember) {
    return psiMember instanceof PsiMethod ? JUnitConfiguration.Data.getMethodPresentation((PsiMethod)psiMember)
                                          : super.getMethodPresentation(psiMember);
  }

  @Override
  protected boolean setupConfigurationFromContext(@NotNull JUnitConfiguration configuration,
                                                  @NotNull ConfigurationContext context,
                                                  @NotNull Ref<PsiElement> sourceElement) {
    final LinkedHashSet<String> classes = new LinkedHashSet<>();
    final PsiElement element = checkPatterns(context, classes);
    if (element == null) {
      return false;
    }
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(context.getProject());
    GlobalSearchScope resolveScope = element.getResolveScope();
    if (psiFacade.findClass(JUnitUtil.TEST_CASE_CLASS, resolveScope) == null &&
        psiFacade.findClass(JUnitUtil.TEST5_ANNOTATION, resolveScope) == null) {
      return false;
    }
    sourceElement.set(element);
    final JUnitConfiguration.Data data = configuration.getPersistentData();
    data.setPatterns(classes);
    data.TEST_OBJECT = JUnitConfiguration.TEST_PATTERN;
    data.setScope(setupPackageConfiguration(context, configuration, data.getScope()));
    configuration.setGeneratedName();
    final Location contextLocation = context.getLocation();
    if (contextLocation instanceof PsiMemberParameterizedLocation) {
      final String paramSetName = ((PsiMemberParameterizedLocation)contextLocation).getParamSetName();
      if (paramSetName != null) {
        configuration.setProgramParameters(paramSetName);
      }
    }
    return true;
  }

  @Override
  protected boolean isApplicableTestType(String type, ConfigurationContext context) {
    return JUnitConfiguration.TEST_PATTERN.equals(type);
  }

  @Override
  protected Module findModule(JUnitConfiguration configuration, Module contextModule) {
    final Set<String> patterns = configuration.getPersistentData().getPatterns();
    return findModule(configuration, contextModule, patterns);
  }

  @Override
  public boolean isConfigurationFromContext(@NotNull JUnitConfiguration unitConfiguration, @NotNull ConfigurationContext context) {
     if (!isApplicableTestType(unitConfiguration.getTestType(), context)) return false;
    if (differentParamSet(unitConfiguration, context.getLocation())) return false;
    final Set<String> patterns = unitConfiguration.getPersistentData().getPatterns();
    if (isConfiguredFromContext(context, patterns)) return true;
    return false;
  }

  @Override
  protected boolean isRequiredVisibility(PsiMember psiElement) {
    if (JUnitUtil.isJUnit5(psiElement)) {
      return true;
    }
    return super.isRequiredVisibility(psiElement);
  }
}
