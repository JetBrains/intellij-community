package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class UnnecessaryInterfaceModifierInspection extends BaseInspection {
  private final static Set<String> INTERFACE_REDUNDANT_MODIFIERS = new HashSet<String>(
    Arrays.asList(new String[]{PsiModifier.ABSTRACT}));
  private final static Set<String> CLASS_REDUNDANT_MODIFIERS = new HashSet<String>(
    Arrays.asList(new String[]{PsiModifier.PUBLIC, PsiModifier.STATIC}));
  private final static Set<String> FIELD_REDUNDANT_MODIFIERS = new HashSet<String>(
    Arrays.asList(new String[]{PsiModifier.PUBLIC, PsiModifier.STATIC, PsiModifier.FINAL}));
  private final static Set<String> METHOD_REDUNDANT_MODIFIERS = new HashSet<String>(
    Arrays.asList(new String[]{PsiModifier.PUBLIC, PsiModifier.ABSTRACT}));

  public String getDisplayName() {
    return "Unnecessary interface modifier";
  }

  public String getGroupDisplayName() {
    return GroupNames.VERBOSE_GROUP_NAME;
  }

  public ProblemDescriptor[] doCheckClass(PsiClass aClass, InspectionManager mgr, boolean isOnTheFly) {
    if (!aClass.isPhysical()) {
      return super.doCheckClass(aClass, mgr, isOnTheFly);
    }
    final BaseInspectionVisitor visitor = createVisitor(mgr, isOnTheFly);
    aClass.accept(visitor);

    return visitor.getErrors();
  }

  public ProblemDescriptor[] doCheckMethod(PsiMethod method, InspectionManager mgr, boolean isOnTheFly) {
    if (!method.isPhysical()) {
      return super.doCheckMethod(method, mgr, isOnTheFly);
    }
    final BaseInspectionVisitor visitor = createVisitor(mgr, isOnTheFly);
    method.accept(visitor);
    return visitor.getErrors();
  }

  public ProblemDescriptor[] doCheckField(PsiField field, InspectionManager mgr, boolean isOnTheFly) {
    if (!field.isPhysical()) {
      return super.doCheckField(field, mgr, isOnTheFly);
    }
    final BaseInspectionVisitor visitor = createVisitor(mgr, isOnTheFly);
    field.accept(visitor);
    return visitor.getErrors();
  }

  public String buildErrorString(PsiElement location) {
    final PsiModifierList modifierList;
    if (location instanceof PsiModifierList) {
      modifierList = (PsiModifierList)location;
    }
    else {
      modifierList = (PsiModifierList)location.getParent();
    }
    final PsiElement parent = modifierList.getParent();
    if (parent instanceof PsiClass) {
      return "Modifier '#ref' is redundant for interfaces #loc";
    }
    else if (parent instanceof PsiMethod) {
      if (modifierList.getChildren().length > 1) {
        return "Modifiers '#ref' are redundant for interface methods #loc";
      }
      else {
        return "Modifier '#ref' is redundant for interface methods #loc";
      }
    }
    else {
      if (modifierList.getChildren().length > 1) {
        return "Modifiers '#ref' are redundant for interface fields #loc";
      }
      else {
        return "Modifier '#ref' is redundant for interface fields #loc";
      }
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryInterfaceModifierVisitor();
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return new UnnecessaryInterfaceModifersFix(location);
  }

  private static class UnnecessaryInterfaceModifersFix extends InspectionGadgetsFix {
    private final String m_name;

    private UnnecessaryInterfaceModifersFix(PsiElement fieldModifiers) {
      super();
      m_name = "Remove '" + fieldModifiers.getText() + '\'';
    }

    public String getName() {
      return m_name;
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
      if (isQuickFixOnReadOnlyFile(descriptor)) return;
      try {
        final PsiElement element = descriptor.getPsiElement();
        final PsiModifierList modifierList;
        if (element instanceof PsiModifierList) {
          modifierList = (PsiModifierList)element;
        }
        else {
          modifierList = (PsiModifierList)element.getParent();
        }
        modifierList.setModifierProperty(PsiModifier.STATIC, false);
        modifierList.setModifierProperty(PsiModifier.ABSTRACT, false);
        modifierList.setModifierProperty(PsiModifier.FINAL, false);
        if (!(modifierList.getParent() instanceof PsiClass)) {
          modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
        }
      }
      catch (IncorrectOperationException e) {
        final Class aClass = getClass();
        final String className = aClass.getName();
        final Logger logger = Logger.getInstance(className);
        logger.error(e);
      }
    }
  }

  private static class UnnecessaryInterfaceModifierVisitor extends BaseInspectionVisitor {

    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass.isInterface()) {
        final PsiModifierList modifiers = aClass.getModifierList();
        checkForRedundantModifiers(modifiers, INTERFACE_REDUNDANT_MODIFIERS);
      }
      final PsiClass parent = ClassUtils.getContainingClass(aClass);
      if (parent != null && parent.isInterface()) {
        final PsiModifierList modifiers = aClass.getModifierList();
        checkForRedundantModifiers(modifiers, CLASS_REDUNDANT_MODIFIERS);
      }
    }

    public void visitField(@NotNull PsiField field) {
      // don't call super, to keep this from drilling in
      final PsiClass containingClass = field.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (!containingClass.isInterface()) {
        return;
      }
      final PsiModifierList modifiers = field.getModifierList();
      checkForRedundantModifiers(modifiers, FIELD_REDUNDANT_MODIFIERS);
    }

    public void visitMethod(@NotNull PsiMethod method) {
      // don't call super, to keep this from drilling in
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      if (!aClass.isInterface()) {
        return;
      }
      final PsiModifierList modifiers = method.getModifierList();
      checkForRedundantModifiers(modifiers, METHOD_REDUNDANT_MODIFIERS);
    }

    public void checkForRedundantModifiers(PsiModifierList list, Set<String> modifiers) {
      if (list == null) return;
      final PsiElement[] children = list.getChildren();
      for (PsiElement child : children) {
        if (modifiers.contains(child.getText())) {
          registerError(child);
        }
      }
    }
  }
}
