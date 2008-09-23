package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.Introspector;

/**
 * @author ilyas
 */
public class GroovyPropertyUtils {

  public static PsiMethod findSetterForField(PsiField field) {
    final PsiClass containingClass = field.getContainingClass();
    final Project project = field.getProject();
    final String propertyName = PropertyUtil.suggestPropertyName(project, field);
    final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    return findPropertySetter(containingClass, propertyName, isStatic, true);
  }

  public static PsiMethod findGetterForField(PsiField field) {
    final PsiClass containingClass = field.getContainingClass();
    final Project project = field.getProject();
    final String propertyName = PropertyUtil.suggestPropertyName(project, field);
    final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    return PropertyUtil.findPropertyGetter(containingClass, propertyName, isStatic, true);
  }


  @Nullable
  public static PsiMethod findPropertySetter(PsiClass aClass, String propertyName, boolean isStatic, boolean checkSuperClasses) {
    if (aClass == null) return null;
    PsiMethod[] methods;
    if (checkSuperClasses) {
      methods = aClass.getAllMethods();
    } else {
      methods = aClass.getMethods();
    }

    for (PsiMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.STATIC) != isStatic) continue;

      if (PsiUtil.isSimplePropertySetter(method)) {
        if (getPropertyNameBySetter(method).equals(propertyName)) {
          return method;
        }
      }
    }

    return null;
  }

  @Nullable
  public static PsiMethod findPropertyGetter(PsiClass aClass, String propertyName, boolean isStatic, boolean checkSuperClasses) {
    if (aClass == null) return null;
    PsiMethod[] methods;
    if (checkSuperClasses) {
      methods = aClass.getAllMethods();
    } else {
      methods = aClass.getMethods();
    }

    for (PsiMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.STATIC) != isStatic) continue;

      if (PsiUtil.isSimplePropertyGetter(method)) {
        if (getPropertyNameByGetter(method).equals(propertyName)) {
          return method;
        }
      }
    }

    return null;
  }

  @NotNull
  public static String getPropertyNameByGetter(PsiMethod getterMethod) {
    @NonNls String methodName = getterMethod.getName();
    return methodName.startsWith("get")
           ? StringUtil.decapitalize(methodName.substring(3))
           : StringUtil.decapitalize(methodName.substring(2));
  }

  @NotNull
  public static String getPropertyNameBySetter(PsiMethod setterMethod) {
    String methodName = setterMethod.getName();
    return Introspector.decapitalize(methodName.substring(3));
  }


  @Nullable
  public static String getPropertyName(PsiMethod method) {
    if (PsiUtil.isSimplePropertyGetter(method)) {
      return getPropertyNameByGetter(method);
    } else if (PsiUtil.isSimplePropertySetter(method)) {
      return getPropertyNameBySetter(method);
    } else {
      return null;
    }
  }

  public static boolean isSimplePropertyAccessor(PsiMethod method) {
    return PropertyUtil.isSimplePropertyGetter(method) || isSimplePropertySetter(method);
  }

  @SuppressWarnings("HardCodedStringLiteral")
  public static boolean isSimplePropertySetter(PsiMethod method) {
    if (method == null) return false;

    if (method.isConstructor()) return false;

    String methodName = method.getName();

    if (!(methodName.startsWith("set") && methodName.length() > "set".length())) return false;
    if (Character.isLowerCase(methodName.charAt("set".length()))) return false;

    if (method.getParameterList().getParametersCount() != 1) {
      return false;
    }

    return method.getReturnType() == null || method.getReturnType() == PsiType.VOID || method.getReturnTypeElement() == null;
  }

}
