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

import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PatternConfigurationProducer extends JUnitConfigurationProducer {


  private PsiElement[] myElements;

  protected RunnerAndConfigurationSettings createConfigurationByElement(final Location location, final ConfigurationContext context) {
    final Project project = location.getProject();
    final LinkedHashSet<String> classes = new LinkedHashSet<String>();
    myElements = collectPatternElements(context, classes);
    if (classes.size() <= 1) return null;
    RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(project, context);
    final JUnitConfiguration configuration = (JUnitConfiguration)settings.getConfiguration();
    final JUnitConfiguration.Data data = configuration.getPersistentData();
    data.getPatterns().addAll(classes);
    data.TEST_OBJECT = JUnitConfiguration.TEST_PATTERN;
    data.setScope(setupPackageConfiguration(context, project, configuration, data.getScope()));
    configuration.setGeneratedName();
    JavaRunConfigurationExtensionManager.getInstance().extendCreatedConfiguration(configuration, location);
    return settings;
  }

  static Set<PsiMember> collectTestMembers(PsiElement[] psiElements) {
    final Set<PsiMember> foundMembers = new LinkedHashSet<PsiMember>();
    for (PsiElement psiElement : psiElements) {
      if (psiElement instanceof PsiClassOwner) {
        final PsiClass[] classes = ((PsiClassOwner)psiElement).getClasses();
        for (PsiClass aClass : classes) {
          if (JUnitUtil.isTestClass(aClass)) {
            foundMembers.add(aClass);
          }
        }
      } else if (psiElement instanceof PsiClass) {
        if (JUnitUtil.isTestClass((PsiClass)psiElement)) {
          foundMembers.add((PsiClass)psiElement);
        }
      } else if (psiElement instanceof PsiMethod) {
        if (JUnitUtil.getTestMethod(psiElement) != null) {
          foundMembers.add((PsiMethod)psiElement);
        }
      }
    }
    return foundMembers;
  }

  public static boolean isMultipleElementsSelected(ConfigurationContext context) {
    final LinkedHashSet<String> classes = new LinkedHashSet<String>();
    final PsiElement[] elements = collectPatternElements(context, classes);
    if (elements != null && collectTestMembers(elements).size() > 1) {
      return true;
    }
    return false;
  }
  
  private static PsiElement[] collectPatternElements(ConfigurationContext context, LinkedHashSet<String> classes) {
    final DataContext dataContext = context.getDataContext();
    PsiElement[] elements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
    if (elements != null) {
      for (PsiMember psiClass : collectTestMembers(elements)) {
        classes.add(getQName(psiClass));
      }
      return elements;
    } else {
      final VirtualFile[] files = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
      if (files != null) {
        final List<PsiFile> psiFiles = new ArrayList<PsiFile>();
        final PsiManager psiManager = PsiManager.getInstance(context.getProject());
        for (VirtualFile file : files) {
          final PsiFile psiFile = psiManager.findFile(file);
          if (psiFile instanceof PsiClassOwner) {
            for (PsiMember psiMember : collectTestMembers(((PsiClassOwner)psiFile).getClasses())) {
              classes.add(((PsiClass)psiMember).getQualifiedName());
            }
            psiFiles.add(psiFile);
          }
        }
        return psiFiles.toArray(new PsiElement[psiFiles.size()]);
      }
    }
    return null;
  }

  public static String getQName(PsiMember psiMember) {
    if (psiMember instanceof PsiClass) {
      return ((PsiClass)psiMember).getQualifiedName();
    }
    else {
      return psiMember.getContainingClass().getQualifiedName() + "," + psiMember.getName();
    }
  }

  public PsiElement getSourceElement() {
    return myElements[0];
  }

  @Override
  protected RunnerAndConfigurationSettings findExistingByElement(@NotNull Location location,
                                                                 @NotNull RunnerAndConfigurationSettings[] existingConfigurations,
                                                                 ConfigurationContext context) {
    final LinkedHashSet<String> classes = new LinkedHashSet<String>();
    collectPatternElements(context, classes);
    for (RunnerAndConfigurationSettings existingConfiguration : existingConfigurations) {
      final JUnitConfiguration unitConfiguration = (JUnitConfiguration)existingConfiguration.getConfiguration();
      final TestObject testobject = unitConfiguration.getTestObject();
      if (testobject instanceof TestsPattern) {
        if (Comparing.equal(classes, unitConfiguration.getPersistentData().getPatterns())) {
          return existingConfiguration;
        }
      }
    }
    return null;
  }
}
