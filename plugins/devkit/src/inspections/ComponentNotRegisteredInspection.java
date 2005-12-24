/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.quickfix.RegisterActionFix;
import org.jetbrains.idea.devkit.inspections.quickfix.RegisterComponentFix;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.util.ComponentType;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.util.Set;

/**
 * @author swr
 */
public class ComponentNotRegisteredInspection extends DevKitInspectionBase {
  public boolean CHECK_ACTIONS = true;
  public boolean IGNORE_NON_PUBLIC = true;

  public String getDisplayName() {
    return DevKitBundle.message("inspections.component.not.registered.name");
  }

  @NonNls
  public String getShortName() {
    return "ComponentNotRegistered";
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  @Nullable
  public JComponent createOptionsPanel() {
    final JPanel jPanel = new JPanel();
    jPanel.setLayout(new BoxLayout(jPanel, BoxLayout.Y_AXIS));

    final JCheckBox checkJavaActions = new JCheckBox(
            DevKitBundle.message("inspections.component.not.registered.option.check.actions"),
            CHECK_ACTIONS);
    checkJavaActions.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        CHECK_ACTIONS = checkJavaActions.isSelected();
      }
    });

    final JCheckBox ignoreNonPublic = new JCheckBox(
            DevKitBundle.message("inspections.component.not.registered.option.ignore.non.public"),
            IGNORE_NON_PUBLIC);
    ignoreNonPublic.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        IGNORE_NON_PUBLIC = ignoreNonPublic.isSelected();
      }
    });

    jPanel.add(checkJavaActions);
    jPanel.add(ignoreNonPublic);
    return jPanel;
  }

  @Nullable
  public ProblemDescriptor[] checkClass(PsiClass checkedClass, InspectionManager manager, boolean isOnTheFly) {
    if (checkedClass.getQualifiedName() != null &&
            checkedClass.getNameIdentifier() != null &&
            checkedClass.getContainingFile().getVirtualFile() != null &&
            !isAbstract(checkedClass)) {
      if (IGNORE_NON_PUBLIC && !isPublic(checkedClass)) {
        return null;
      }

      final PsiManager psiManager = checkedClass.getManager();
      final GlobalSearchScope scope = checkedClass.getResolveScope();

      if (PsiUtil.isInnerClass(checkedClass)) {
        // don't check inner classes (make this an option?)
        return null;
      }

      if (CHECK_ACTIONS) {
        final PsiClass actionClass = psiManager.findClass(AnAction.class.getName(), scope);
        if (actionClass == null) {
          // stop if action class cannot be found (non-devkit module/project)
          return null;
        }
        if (checkedClass.isInheritor(actionClass, true)) {
          if (!isActionRegistered(checkedClass)) {
            final LocalQuickFix fix = canFix(checkedClass) ? new RegisterActionFix(checkedClass) : null;
            final ProblemDescriptor problem = manager.createProblemDescriptor(
                    checkedClass.getNameIdentifier(),
                    DevKitBundle.message("inspections.component.not.registered.message",
                            DevKitBundle.message("new.menu.action.text")),
                    fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            return new ProblemDescriptor[]{problem};
          } else {
            // action IS registered, stop here
            return null;
          }
        }
      }

      final ComponentType[] types = ComponentType.values();
      for (ComponentType type : types) {
        final PsiClass compClass = psiManager.findClass(type.myClassName, scope);
        if (compClass == null) {
          // stop if component classes cannot be found (non-devkit module/project)
          return null;
        }
        if (checkedClass.isInheritor(compClass, true)) {
          if (getRegistrationTypes(checkedClass, false) == null) {
            final LocalQuickFix fix = canFix(checkedClass) ? new RegisterComponentFix(type, checkedClass) : null;
            final ProblemDescriptor problem = manager.createProblemDescriptor(checkedClass.getNameIdentifier(),
                    DevKitBundle.message("inspections.component.not.registered.message",
                            DevKitBundle.message(type.myPropertyKey)),
                    fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
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

  private boolean canFix(PsiClass psiClass) {
    final Project project = psiClass.getProject();
    final Module module = VfsUtil.getModuleForFile(project, psiClass.getContainingFile().getVirtualFile());
    return PluginModuleType.isPluginModuleOrDependency(module);
  }

  private boolean isActionRegistered(PsiClass psiClass) {
    final Set<PsiClass> registrationTypes = getRegistrationTypes(psiClass, true);
    if (registrationTypes != null) {
      for (PsiClass type : registrationTypes) {
        if (AnAction.class.getName().equals(type.getQualifiedName())) return true;
        if (ActionGroup.class.getName().equals(type.getQualifiedName())) return true;
      }
    }
    return false;
  }
}
