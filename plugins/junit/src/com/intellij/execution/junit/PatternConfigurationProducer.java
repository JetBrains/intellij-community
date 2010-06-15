/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.execution.Location;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class PatternConfigurationProducer extends JUnitConfigurationProducer {


  private PsiElement[] myElements;

  protected RunnerAndConfigurationSettings createConfigurationByElement(final Location location, final ConfigurationContext context) {
    final Project project = location.getProject();
    final PsiElement element = location.getPsiElement();
    final StringBuffer buffer = new StringBuffer();
    myElements = collectPatternElements(context, project, element, buffer);
    String pattern = buffer.toString();
    if (pattern == null || pattern.length() == 0) return null;
    RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(project, context);
    final JUnitConfiguration configuration = (JUnitConfiguration)settings.getConfiguration();
    final JUnitConfiguration.Data data = configuration.getPersistentData();
    data.PATTERN = pattern;
    data.TEST_OBJECT = JUnitConfiguration.TEST_PATTERN;
    data.setScope(setupPackageConfiguration(context, project, configuration, data.getScope()));
    configuration.setGeneratedName();
    RunConfigurationExtension.patchCreatedConfiguration(configuration);
    return settings;
  }

  private PsiElement[] collectPatternElements(ConfigurationContext context, Project project, PsiElement element, StringBuffer buf) {
    PsiElement[] elements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(context.getDataContext());
    if (elements != null && elements.length > 1) {
      for (PsiElement psiElement : elements) {
        if (psiElement instanceof PsiClass) {
          buf.append (buf.length() > 0 ? "||": "").append(((PsiClass)psiElement).getQualifiedName());
        }
      }
      return elements;
    }
    return null;
  }

  public PsiElement getSourceElement() {
    return myElements[0];
  }

  @Override
  protected RunnerAndConfigurationSettings findExistingByElement(@NotNull Location location,
                                                                 @NotNull RunnerAndConfigurationSettings[] existingConfigurations,
                                                                 ConfigurationContext context) {
    final StringBuffer buffer = new StringBuffer();
    collectPatternElements(context, context.getProject(), location.getPsiElement(), buffer);
    final String pattern = buffer.toString();
    for (RunnerAndConfigurationSettings existingConfiguration : existingConfigurations) {
      final JUnitConfiguration unitConfiguration = (JUnitConfiguration)existingConfiguration.getConfiguration();
      final TestObject testobject = unitConfiguration.getTestObject();
      if (testobject instanceof TestsPattern) {
        if (Comparing.strEqual(pattern, unitConfiguration.getPersistentData().getPattern())) {
          return existingConfiguration;
        }
      }
    }
    return null;
  }
}
