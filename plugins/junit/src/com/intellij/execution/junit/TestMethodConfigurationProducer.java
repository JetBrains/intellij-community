/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TestMethodConfigurationProducer extends JUnitConfigurationProducer {
  private Location<PsiMethod> myMethodLocation;

  protected RunnerAndConfigurationSettings createConfigurationByElement(final Location location, final ConfigurationContext context) {
    final Project project = location.getProject();

    myMethodLocation = getTestMethod(location);
    if (myMethodLocation == null) return null;
    RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(project, context);
    final JUnitConfiguration configuration = (JUnitConfiguration)settings.getConfiguration();
    setupConfigurationModule(context, configuration);
    final Module originalModule = configuration.getConfigurationModule().getModule();
    configuration.beMethodConfiguration(myMethodLocation);
    configuration.restoreOriginalModule(originalModule);
    JavaRunConfigurationExtensionManager.getInstance().extendCreatedConfiguration(configuration, location);
    return settings;
  }

  public PsiElement getSourceElement() {
    return myMethodLocation.getPsiElement();
  }

  private static Location<PsiMethod> getTestMethod(final Location<?> location) {
    for (Iterator<Location<PsiMethod>> iterator = location.getAncestors(PsiMethod.class, false); iterator.hasNext();) {
      final Location<PsiMethod> methodLocation = iterator.next();
      if (JUnitUtil.isTestMethod(methodLocation, false)) return methodLocation;
    }
    return null;
  }

  @Override
  public void perform(final ConfigurationContext context, final Runnable performRunnable) {
    final PsiMethod psiMethod = myMethodLocation.getPsiElement();
    final PsiClass containingClass = psiMethod.getContainingClass();
    if (containingClass != null && containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      final List<PsiClass> classes = new ArrayList<PsiClass>();
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        @Override
        public void run() {
          ClassInheritorsSearch.search(containingClass).forEach(new Processor<PsiClass>() {
            @Override
            public boolean process(PsiClass aClass) {
              if (!aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                classes.add(aClass);
              }
              return true;
            }
          });
        }
      }, "Search for " + containingClass.getQualifiedName() + " inheritors", true, containingClass.getProject())) {
        return;
      }

      if (classes.size() == 1) {
        runForClass(classes.get(0), psiMethod, context, performRunnable);
        return;
      }
      //suggest to run all inherited tests 
      classes.add(0, null);
      final JBList list = new JBList(classes);
      list.setCellRenderer(new PsiClassListCellRenderer() {
        @Override
        protected boolean customizeNonPsiElementLeftRenderer(ColoredListCellRenderer renderer,
                                                             JList list,
                                                             Object value,
                                                             int index,
                                                             boolean selected,
                                                             boolean hasFocus) {
          if (value == null) {
            renderer.append("All");
            return true;
          }
          return super.customizeNonPsiElementLeftRenderer(renderer, list, value, index, selected, hasFocus);
        }
      });
      JBPopupFactory.getInstance().createListPopupBuilder(list)
        .setTitle("Choose executable classes to run " + psiMethod.getName())
        .setMovable(false)
        .setResizable(false)
        .setRequestFocus(true)
        .setItemChoosenCallback(new Runnable() {
          public void run() {
            final Object[] values = list.getSelectedValues();
            if (values == null) return;
            runMethod(values, psiMethod, context, performRunnable, classes);
          }
        }).createPopup().showInBestPositionFor(context.getDataContext());
      return;
    }
    super.perform(context, performRunnable);
  }

  private static void runMethod(Object[] values,
                                PsiMethod psiMethod,
                                ConfigurationContext context,
                                Runnable performRunnable,
                                List<PsiClass> classes) {
    if (values.length == 1) {
      final Object value = values[0];
      if (value instanceof PsiClass) {
        runForClass((PsiClass)value, psiMethod, context, performRunnable);
      }
      else {
        runForClasses(classes, psiMethod, context, performRunnable);
      }
      return;
    }
    if (ArrayUtil.contains(null, values)) {
      runForClasses(classes, psiMethod, context, performRunnable);
    }
    else {
      final List<PsiClass> selectedClasses = new ArrayList<PsiClass>();
      for (Object value : values) {
        if (value instanceof PsiClass) {
          selectedClasses.add((PsiClass)value);
        }
      }
      runForClasses(selectedClasses, psiMethod, context, performRunnable);
    }
  }

  private static void runForClasses(List<PsiClass> classes, PsiMethod method, ConfigurationContext context, Runnable performRunnable) {
    classes.remove(null);
    ((JUnitConfiguration)context.getConfiguration().getConfiguration()).bePatternConfiguration(classes, method);
    performRunnable.run();
  }

  private static void runForClass(final PsiClass aClass,
                                  final PsiMethod psiMethod, 
                                  final ConfigurationContext context,
                                  final Runnable performRunnable) {
    final Project project = psiMethod.getProject();
    ((JUnitConfiguration)context.getConfiguration().getConfiguration()).beMethodConfiguration(
      new MethodLocation(project, psiMethod,
                         new PsiLocation<PsiClass>(project, aClass)));
    performRunnable.run();
  }
}

