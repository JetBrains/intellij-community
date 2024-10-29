// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
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
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.quickfix.RegisterActionFix;
import org.jetbrains.idea.devkit.inspections.quickfix.RegisterComponentFix;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.util.ComponentType;

import java.util.Map;
import java.util.Set;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

final class ComponentNotRegisteredInspection extends DevKitJvmInspection.ForClass {
  private static final Logger LOG = Logger.getInstance(ComponentNotRegisteredInspection.class);

  public boolean CHECK_ACTIONS = true;
  public boolean IGNORE_NON_PUBLIC = true;

  private static final Map<ComponentType, RegistrationCheckerUtil.RegistrationType> COMPONENT_TYPE_TO_REGISTRATION_TYPE =
    Map.of(
      ComponentType.APPLICATION, RegistrationCheckerUtil.RegistrationType.APPLICATION_COMPONENT,
      ComponentType.PROJECT, RegistrationCheckerUtil.RegistrationType.PROJECT_COMPONENT,
      ComponentType.MODULE, RegistrationCheckerUtil.RegistrationType.MODULE_COMPONENT);

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("IGNORE_NON_PUBLIC", DevKitBundle.message("inspections.component.not.registered.option.ignore.non.public")),
      checkbox("CHECK_ACTIONS", DevKitBundle.message("inspections.component.not.registered.option.check.actions"))
    );
  }

  @Override
  protected void checkClass(@NotNull Project project, @NotNull PsiClass checkedClass, @NotNull HighlightSink sink) {
    if (checkedClass.getQualifiedName() == null ||
        checkedClass.getContainingFile().getVirtualFile() == null ||
        checkedClass.hasModifierProperty(PsiModifier.ABSTRACT) ||
        checkedClass.isEnum() ||
        checkedClass.isDeprecated() ||
        PsiUtil.isInnerClass(checkedClass) ||
        !shouldCheckActionClass(checkedClass)) {
      return;
    }

    GlobalSearchScope scope = checkedClass.getResolveScope();
    PsiClass actionClass = JavaPsiFacade.getInstance(project).findClass(AnAction.class.getName(), scope);
    if (actionClass == null) {
      // stop if action class cannot be found (non-devkit module/project)
      return;
    }

    if (checkedClass.isInheritor(actionClass, true)) {
      if (!isActionRegistered(checkedClass) && canFix(checkedClass)) {
        LocalQuickFix fix = new RegisterActionFix(checkedClass);
        sink.highlight(DevKitBundle.message("inspections.component.not.registered.message",
                                            DevKitBundle.message("new.menu.action.text")), fix);
      }
      // action IS registered, stop here
      return;
    }

    //noinspection deprecation
    PsiClass baseComponentClass = JavaPsiFacade.getInstance(project).findClass(BaseComponent.class.getName(), scope);
    if (baseComponentClass == null) {
      // stop if component class cannot be found (non-devkit module/project)
      return;
    }

    // if directly implements BaseComponent, check that registered as some component
    if (checkedClass.isInheritor(baseComponentClass, false)) {
      if (findRegistrationType(checkedClass, RegistrationCheckerUtil.RegistrationType.ALL_COMPONENTS) == null && canFix(checkedClass)) {
        sink.highlight(DevKitBundle.message("inspections.component.not.registered.message", "Component")); //NON-NLS
      }
      return;
    }

    if (!checkedClass.isInheritor(baseComponentClass, true)) {
      return;
    }

    for (ComponentType componentType : ComponentType.values()) {
      if (InheritanceUtil.isInheritor(checkedClass, componentType.myClassName) &&
          checkComponentRegistration(checkedClass, sink, componentType)) {
        return;
      }
    }
  }

  private static boolean checkComponentRegistration(@NotNull PsiClass checkedClass,
                                                    @NotNull HighlightSink sink,
                                                    @NotNull ComponentType componentType) {
    if (findRegistrationType(checkedClass, COMPONENT_TYPE_TO_REGISTRATION_TYPE.get(componentType)) != null) {
      return true;
    }
    if (!canFix(checkedClass)) {
      return true;
    }

    LocalQuickFix fix = new RegisterComponentFix(componentType, checkedClass);
    sink.highlight(DevKitBundle.message("inspections.component.not.registered.message",
                                        DevKitBundle.message(componentType.myPropertyKey)), fix);
    return false;
  }

  private static PsiClass findRegistrationType(@NotNull PsiClass checkedClass, @NotNull RegistrationCheckerUtil.RegistrationType type) {
    final Set<PsiClass> types = RegistrationCheckerUtil.getRegistrationTypes(checkedClass, type);
    return ContainerUtil.getFirstItem(types);
  }

  private boolean shouldCheckActionClass(@NotNull PsiClass psiClass) {
    if (!CHECK_ACTIONS) return false;
    if (IGNORE_NON_PUBLIC && !psiClass.hasModifierProperty(PsiModifier.PUBLIC)) return false;
    return true;
  }

  private static boolean isActionRegistered(@NotNull PsiClass actionClass) {
    final PsiClass registrationType = findRegistrationType(actionClass, RegistrationCheckerUtil.RegistrationType.ACTION);
    if (registrationType != null) {
      return true;
    }

    // search code usages: 1) own CTOR calls  2) usage via "new ActionClass()"
    for (PsiMethod method : actionClass.getConstructors()) {
      final Query<PsiReference> search = MethodReferencesSearch.search(method, method.getUseScope(), true);
      if (search.findFirst() != null) {
        return true;
      }
    }

    final Query<PsiReference> search = ReferencesSearch.search(actionClass, actionClass.getUseScope());
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

  private static boolean canFix(@NotNull PsiClass psiClass) {
    Project project = psiClass.getProject();
    PsiFile psiFile = psiClass.getContainingFile();
    LOG.assertTrue(psiFile != null);
    Module module = ModuleUtilCore.findModuleForFile(psiFile.getVirtualFile(), project);
    return PluginModuleType.isPluginModuleOrDependency(module) ||
           module != null && org.jetbrains.idea.devkit.util.PsiUtil.isPluginModule(module);
  }
}
