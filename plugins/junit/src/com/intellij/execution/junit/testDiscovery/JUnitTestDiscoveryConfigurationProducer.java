// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit.testDiscovery;

import com.intellij.execution.JavaTestConfigurationBase;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit.TestsPattern;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testDiscovery.TestDiscoveryConfigurationProducer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

public class JUnitTestDiscoveryConfigurationProducer extends TestDiscoveryConfigurationProducer {
  protected JUnitTestDiscoveryConfigurationProducer() {
    super(JUnitConfigurationType.getInstance());
  }

  @Override
  protected void setPosition(JavaTestConfigurationBase configuration, PsiLocation<PsiMethod> position) {
    ((JUnitConfiguration)configuration).beFromSourcePosition(position);
  }

  @Override
  protected Pair<String, String> getPosition(JavaTestConfigurationBase configuration) {
    final JUnitConfiguration.Data data = ((JUnitConfiguration)configuration).getPersistentData();
    if (data.TEST_OBJECT.equals(JUnitConfiguration.BY_SOURCE_POSITION)) {
      return Pair.create(data.getMainClassName(), data.getMethodName());
    }
    return null;
  }

  @Override
  public boolean isApplicable(@NotNull PsiMethod method) {
    return JUnitUtil.isTestMethod(new PsiLocation<>(method));
  }

  @NotNull
  @Override
  public RunProfileState createProfile(@NotNull PsiMethod[] testMethods,
                                       Module module,
                                       RunConfiguration configuration,
                                       ExecutionEnvironment environment) {
    JUnitConfiguration.Data data = ((JUnitConfiguration)configuration).getPersistentData();
    data.setPatterns(
      Arrays.stream(testMethods)
            .map(method -> method.getContainingClass().getQualifiedName() + "," + method.getName())
            .collect(Collectors.toCollection(LinkedHashSet::new)));
    data.TEST_OBJECT = JUnitConfiguration.TEST_PATTERN;
    return new TestsPattern((JUnitConfiguration)configuration, environment);
  }
}
