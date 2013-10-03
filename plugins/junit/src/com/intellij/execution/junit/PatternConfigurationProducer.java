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

import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PatternConfigurationProducer extends JUnitConfigurationProducer {
  @Override
  protected boolean setupConfigurationFromContext(JUnitConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    final LinkedHashSet<String> classes = new LinkedHashSet<String>();
    PsiElement[] elements = collectPatternElements(context, classes);
    if (classes.size() <= 1) return false;
    sourceElement.set(elements[0]);
    final JUnitConfiguration.Data data = configuration.getPersistentData();
    data.setPatterns(classes);
    data.TEST_OBJECT = JUnitConfiguration.TEST_PATTERN;
    data.setScope(setupPackageConfiguration(context, configuration, data.getScope()));
    configuration.setGeneratedName();
    return true;
  }

  @Override
  protected Module findModule(JUnitConfiguration configuration, Module contextModule) {
    final Set<String> patterns = configuration.getPersistentData().getPatterns();
    return findModule(configuration, contextModule, patterns);
  }

  public static Module findModule(ModuleBasedConfiguration configuration, Module contextModule, Set<String> patterns) {
    return JavaExecutionUtil.findModule(contextModule, patterns, configuration.getProject(), new Condition<PsiClass>() {
      @Override
      public boolean value(PsiClass psiClass) {
        return JUnitUtil.isTestClass(psiClass);
      }
    });
  }

  static Set<PsiElement> collectTestMembers(PsiElement[] psiElements) {
    final Set<PsiElement> foundMembers = new LinkedHashSet<PsiElement>();
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
          foundMembers.add(psiElement);
        }
      } else if (psiElement instanceof PsiMethod) {
        if (JUnitUtil.getTestMethod(psiElement) != null) {
          foundMembers.add(psiElement);
        }
      } else if (psiElement instanceof PsiDirectory) {
        final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)psiElement);
        if (aPackage != null) {
          foundMembers.add(aPackage);
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
      for (PsiElement psiClass : collectTestMembers(elements)) {
        classes.add(getQName(psiClass));
      }
      return elements;
    } else {
      final VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
      if (files != null) {
        final List<PsiFile> psiFiles = new ArrayList<PsiFile>();
        final PsiManager psiManager = PsiManager.getInstance(context.getProject());
        for (VirtualFile file : files) {
          final PsiFile psiFile = psiManager.findFile(file);
          if (psiFile instanceof PsiClassOwner) {
            for (PsiElement psiMember : collectTestMembers(((PsiClassOwner)psiFile).getClasses())) {
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

  public static String getQName(PsiElement psiMember) {
    if (psiMember instanceof PsiClass) {
      return ((PsiClass)psiMember).getQualifiedName();
    }
    else if (psiMember instanceof PsiMember) {
      return ((PsiMember)psiMember).getContainingClass().getQualifiedName() + "," + ((PsiMember)psiMember).getName();
    } else if (psiMember instanceof PsiPackage) {
      return ((PsiPackage)psiMember).getQualifiedName();
    }
    assert false;
    return null;
  }

  @Override
  public boolean isConfigurationFromContext(JUnitConfiguration unitConfiguration, ConfigurationContext context) {
    final LinkedHashSet<String> classes = new LinkedHashSet<String>();
    collectPatternElements(context, classes);
    final TestObject testobject = unitConfiguration.getTestObject();
    if (testobject instanceof TestsPattern) {
      if (Comparing.equal(classes, unitConfiguration.getPersistentData().getPatterns())) {
        return true;
      }
    }
    return false;
  }
}
