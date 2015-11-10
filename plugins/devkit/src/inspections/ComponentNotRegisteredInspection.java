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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.quickfix.RegisterActionFix;
import org.jetbrains.idea.devkit.inspections.quickfix.RegisterComponentFix;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.util.ComponentType;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class ComponentNotRegisteredInspection extends DevKitInspectionBase {
  public boolean CHECK_ACTIONS = true;
  public boolean IGNORE_NON_PUBLIC = true;
  private static final Logger LOG = Logger.getInstance("org.jetbrains.idea.devkit.inspections.ComponentNotRegisteredInspection");

  @NotNull
  public String getDisplayName() {
    return DevKitBundle.message("inspections.component.not.registered.name");
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return "ComponentNotRegistered";
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @Nullable
  public JComponent createOptionsPanel() {
    JPanel jPanel = new JPanel();
    jPanel.setLayout(new BoxLayout(jPanel, BoxLayout.Y_AXIS));

    final JCheckBox ignoreNonPublic = new JCheckBox(
            DevKitBundle.message("inspections.component.not.registered.option.ignore.non.public"),
            IGNORE_NON_PUBLIC);
    ignoreNonPublic.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        IGNORE_NON_PUBLIC = ignoreNonPublic.isSelected();
      }
    });

    final JCheckBox checkJavaActions = new JCheckBox(
            DevKitBundle.message("inspections.component.not.registered.option.check.actions"),
            CHECK_ACTIONS);
    checkJavaActions.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        boolean selected = checkJavaActions.isSelected();
        CHECK_ACTIONS = selected;
        ignoreNonPublic.setEnabled(selected);
      }
    });

    jPanel.add(checkJavaActions);
    jPanel.add(ignoreNonPublic);
    return jPanel;
  }

  @Nullable
  public ProblemDescriptor[] checkClass(@NotNull PsiClass checkedClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    PsiFile psiFile = checkedClass.getContainingFile();
    PsiIdentifier classIdentifier = checkedClass.getNameIdentifier();
    if (checkedClass.getQualifiedName() != null &&
        classIdentifier != null &&
        psiFile != null &&
        psiFile.getVirtualFile() != null &&
        !isAbstract(checkedClass))
    {
      if (PsiUtil.isInnerClass(checkedClass)) {
        // don't check inner classes (make this an option?)
        return null;
      }

      PsiManager psiManager = checkedClass.getManager();
      GlobalSearchScope scope = checkedClass.getResolveScope();

      if (CHECK_ACTIONS) {
        PsiClass actionClass = JavaPsiFacade.getInstance(psiManager.getProject()).findClass(AnAction.class.getName(), scope);
        if (actionClass == null) {
          // stop if action class cannot be found (non-devkit module/project)
          return null;
        }
        if (checkedClass.isInheritor(actionClass, true)) {
          if (IGNORE_NON_PUBLIC && !isPublic(checkedClass)) {
            return null;
          }
          if (!isActionRegistered(checkedClass) && canFix(checkedClass)) {
            LocalQuickFix fix = new RegisterActionFix(org.jetbrains.idea.devkit.util.PsiUtil.createPointer(checkedClass));
            ProblemDescriptor problem = manager.createProblemDescriptor(
                    classIdentifier,
                    DevKitBundle.message("inspections.component.not.registered.message",
                                         DevKitBundle.message("new.menu.action.text")),
                    fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly);
            return new ProblemDescriptor[]{problem};
          } else {
            // action IS registered, stop here
            return null;
          }
        }
      }

      ComponentType[] types = ComponentType.values();
      for (ComponentType type : types) {
        PsiClass compClass = JavaPsiFacade.getInstance(psiManager.getProject()).findClass(type.myClassName, scope);
        if (compClass == null) {
          // stop if component classes cannot be found (non-devkit module/project)
          return null;
        }
        if (checkedClass.isInheritor(compClass, true)) {
          if (getRegistrationTypes(checkedClass, false) == null && canFix(checkedClass)) {
            LocalQuickFix fix = new RegisterComponentFix(type, org.jetbrains.idea.devkit.util.PsiUtil.createPointer(checkedClass));
            ProblemDescriptor problem = manager.createProblemDescriptor(classIdentifier,
                                                                              DevKitBundle.message("inspections.component.not.registered.message",
                                                                                                   DevKitBundle.message(type.myPropertyKey)),
                                                                              fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly);
            return new ProblemDescriptor[]{problem};
          } else {
            // component IS registered, stop here
            return null;
          }
        }
      }
    }
    return null;
  }

  private static boolean canFix(PsiClass psiClass) {
    Project project = psiClass.getProject();
    PsiFile psiFile = psiClass.getContainingFile();
    LOG.assertTrue(psiFile != null);
    Module module = ModuleUtilCore.findModuleForFile(psiFile.getVirtualFile(), project);
    return PluginModuleType.isPluginModuleOrDependency(module);
  }
}
