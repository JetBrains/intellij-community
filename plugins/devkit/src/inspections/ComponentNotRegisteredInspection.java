/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
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
import java.util.Map;
import java.util.Set;

public class ComponentNotRegisteredInspection extends DevKitInspectionBase {
  public boolean CHECK_ACTIONS = true;
  public boolean IGNORE_NON_PUBLIC = true;
  private static final Logger LOG = Logger.getInstance("org.jetbrains.idea.devkit.inspections.ComponentNotRegisteredInspection");

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
    PsiIdentifier classIdentifier = checkedClass.getNameIdentifier();
    if (classIdentifier != null &&
        checkedClass.getQualifiedName() != null &&
        checkedClass.getContainingFile().getVirtualFile() != null &&
        !checkedClass.hasModifierProperty(PsiModifier.ABSTRACT) &&
        !checkedClass.isEnum() &&
        !PsiUtil.isInnerClass(checkedClass)) {

      GlobalSearchScope scope = checkedClass.getResolveScope();

      if (shouldCheckActionClass(checkedClass)) {
        PsiClass actionClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(AnAction.class.getName(), scope);
        if (actionClass == null) {
          // stop if action class cannot be found (non-devkit module/project)
          return null;
        }
        if (checkedClass.isInheritor(actionClass, true)) {
          if (!isActionRegistered(checkedClass) && canFix(checkedClass)) {
            LocalQuickFix fix = new RegisterActionFix(org.jetbrains.idea.devkit.util.PsiUtil.createPointer(checkedClass));
            ProblemDescriptor problem =
              manager.createProblemDescriptor(classIdentifier,
                                              DevKitBundle.message("inspections.component.not.registered.message",
                                                                   DevKitBundle.message("new.menu.action.text")),
                                              fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly);
            return new ProblemDescriptor[]{problem};
          }
          // action IS registered, stop here
          return null;
        }
      }

      PsiClass compClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(BaseComponent.class.getName(), scope);
      if (compClass == null) {
        // stop if component class cannot be found (non-devkit module/project)
        return null;
      }
      if (!checkedClass.isInheritor(compClass, true)) {
        return null;
      }

      for (ComponentType componentType : ComponentType.values()) {
        if (!InheritanceUtil.isInheritor(checkedClass, componentType.myClassName)) {
          continue;
        }

        if (findRegistrationType(checkedClass, COMPONENT_TYPE_TO_REGISTRATION_TYPE.get(componentType)) != null) {
          return null;
        }

        if (!canFix(checkedClass)) {
          return null;
        }
        LocalQuickFix fix = new RegisterComponentFix(componentType, org.jetbrains.idea.devkit.util.PsiUtil.createPointer(checkedClass));
        ProblemDescriptor problem =
          manager.createProblemDescriptor(classIdentifier,
                                          DevKitBundle.message("inspections.component.not.registered.message",
                                                               DevKitBundle.message(componentType.myPropertyKey)),
                                          fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly);
        return new ProblemDescriptor[]{problem};
      }
    }
    return null;
  }

  private static final Map<ComponentType, RegistrationCheckerUtil.RegistrationType> COMPONENT_TYPE_TO_REGISTRATION_TYPE =
    ContainerUtil.<ComponentType, RegistrationCheckerUtil.RegistrationType>immutableMapBuilder()
      .put(ComponentType.APPLICATION, RegistrationCheckerUtil.RegistrationType.APPLICATION_COMPONENT)
      .put(ComponentType.PROJECT, RegistrationCheckerUtil.RegistrationType.PROJECT_COMPONENT)
      .put(ComponentType.MODULE, RegistrationCheckerUtil.RegistrationType.MODULE_COMPONENT)
      .build();

  private static PsiClass findRegistrationType(@Nullable PsiClass checkedClass, RegistrationCheckerUtil.RegistrationType type) {
    final Set<PsiClass> types = RegistrationCheckerUtil.getRegistrationTypes(checkedClass, type);
    return ContainerUtil.getFirstItem(types);
  }

  private boolean shouldCheckActionClass(PsiClass psiClass) {
    if (!CHECK_ACTIONS) return false;
    if (IGNORE_NON_PUBLIC && !psiClass.hasModifierProperty(PsiModifier.PUBLIC)) return false;
    return true;
  }

  private static boolean isActionRegistered(PsiClass actionClass) {
    final PsiClass registrationType = findRegistrationType(actionClass, RegistrationCheckerUtil.RegistrationType.ACTION);
    if (registrationType != null) {
      return true;
    }

    // search code usages: 1) own CTOR calls  2) usage via "new ActionClass()"
    for (PsiMethod method : actionClass.getConstructors()) {
      final Query<PsiReference> search = MethodReferencesSearch.search(method);
      if (search.findFirst() != null) {
        return true;
      }
    }

    final Query<PsiReference> search = ReferencesSearch.search(actionClass);
    for (PsiReference reference : search) {
      if (!(reference instanceof PsiJavaCodeReferenceElement)) continue;

      final PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(reference.getElement(), PsiNewExpression.class);
      if (newExpression != null) {
        final PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
        if (classReference != null && classReference.getQualifiedName().equals(actionClass.getQualifiedName())) {
          return true;
        }
      }
    }

    return false;
  }

  private static boolean canFix(PsiClass psiClass) {
    Project project = psiClass.getProject();
    PsiFile psiFile = psiClass.getContainingFile();
    LOG.assertTrue(psiFile != null);
    Module module = ModuleUtilCore.findModuleForFile(psiFile.getVirtualFile(), project);
    return PluginModuleType.isPluginModuleOrDependency(module) ||
           module != null && org.jetbrains.idea.devkit.util.PsiUtil.isPluginModule(module);
  }
}
