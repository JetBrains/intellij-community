/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.junit.testDiscovery;

import com.intellij.execution.ExecutionException;
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
import com.intellij.execution.testframework.SearchForTestsTask;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiMethod;

import java.util.Arrays;

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
  public boolean isApplicable(PsiMethod[] methods) {
    return Arrays.stream(methods).anyMatch(method -> JUnitUtil.isTestMethod(new PsiLocation<>(method)));
  }

  @Override
  public RunProfileState createProfile(PsiMethod[] testMethods,
                                       Module module,
                                       RunConfiguration configuration,
                                       ExecutionEnvironment environment) {
    return new TestsPattern((JUnitConfiguration)configuration, environment) {

      @Override
      public SearchForTestsTask createSearchingForTestsTask() {
        return new SearchForTestsTask(configuration.getProject(), myServerSocket) {
          @Override
          protected void search() { }

          @Override
          protected void onFound() throws ExecutionException { 
            addClassesListToJavaParameters(Arrays.asList(testMethods), method -> method.getContainingClass().getQualifiedName() + "," + method.getName(), "", false, getJavaParameters());
          }
        };
      }
    };
  }
}
