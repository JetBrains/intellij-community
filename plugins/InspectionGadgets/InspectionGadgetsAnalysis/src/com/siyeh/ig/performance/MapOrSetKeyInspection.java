package com.siyeh.ig.performance;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public abstract class MapOrSetKeyInspection extends BaseInspection {


  private static final Set<String> SET_METHOD_NAMES = new HashSet<String>() {
    {
      add("add");
      add("addAll");
      add("containsAll");
      add("removeAll");
      add("retainAll");
    }
  };

  private static final Set<String> MAP_METHOD_NAMES = new HashSet<String>() {
    {
      add("put");
      add("putIfAbsent");
      add("compute");
      add("computeIfAbsent");
      add("computeIfPresent");
      add("replace");
      add("remove");
      add("merge");
      add("get");
      add("getOrDefault");
      add("containsKey");
      add("putAll");
    }
  };

  protected abstract boolean shouldTriggerOnKeyType(PsiType argumentType);

  @Override
  public final BaseInspectionVisitor buildVisitor() {
    return new ContainsEntryOfInterestVisitor();
  }

  private static ClassType getClassType(@Nullable PsiClass aClass) {
    return isMapOrSet(aClass, new HashSet<>());
  }

  private static ClassType isMapOrSet(
    @Nullable PsiClass aClass, Set<? super PsiClass> visitedClasses) {
    if (aClass == null) {
      return ClassType.OTHER;
    }
    if (!visitedClasses.add(aClass)) {
      return ClassType.OTHER;
    }
    @NonNls final String className = aClass.getQualifiedName();
    if (CommonClassNames.JAVA_UTIL_SET.equals(className)) {
      return ClassType.SET;
    }
    if (CommonClassNames.JAVA_UTIL_MAP.equals(className)) {
      return ClassType.MAP;
    }
    final PsiClass[] supers = aClass.getSupers();
    for (PsiClass aSuper : supers) {
      final ClassType classType =
        isMapOrSet(aSuper, visitedClasses);
      if (classType != ClassType.OTHER) {
        return classType;
      }
    }
    return ClassType.OTHER;
  }

  enum ClassType {
    SET, MAP, OTHER;

    @NotNull
    public String toString() {
      final String string = super.toString();
      return string.charAt(0) + string.substring(1).toLowerCase();
    }
  }

  private class ContainsEntryOfInterestVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitVariable(PsiVariable variable) {
      super.visitVariable(variable);
      final PsiTypeElement typeElement = variable.getTypeElement();
      if (typeElement == null) {
        return;
      }
      final PsiType type = typeElement.getType();
      if (!(type instanceof PsiClassType)) {
        return;
      }
      final PsiJavaCodeReferenceElement referenceElement =
        typeElement.getInnermostComponentReferenceElement();
      if (referenceElement == null) {
        return;
      }
      final PsiClassType classType = (PsiClassType)type;
      final PsiClass aClass = classType.resolve();

      final ClassType collectionType = getClassType(aClass);
      if (collectionType == ClassType.OTHER) {
        return;
      }

      final PsiReferenceParameterList parameterList =
        referenceElement.getParameterList();
      if (parameterList == null ||
          parameterList.getTypeParameterElements().length == 0) {
        final PsiMember member =
          PsiTreeUtil.getParentOfType(variable, PsiMember.class);
        if (member == null) {
          return;
        }
        final ValueAddedVisitor visitor =
          new ValueAddedVisitor(variable, collectionType);
        member.accept(visitor);
        if (visitor.isValueOfInterestAdded()) {
          registerVariableError(variable, collectionType);
        }
        return;
      }
      final PsiType[] typeArguments = parameterList.getTypeArguments();
      boolean triggerInspection = false;

      if (typeArguments.length < 1 || typeArguments[0] == null) {
        return;
      }

      PsiType typeArgument = typeArguments[0];

      if (shouldTriggerOnKeyType(typeArgument)) {
        triggerInspection = true;
      }

      if (!triggerInspection) {
        return;
      }
      registerVariableError(variable, collectionType);
    }
  }

  private class ValueAddedVisitor
    extends JavaRecursiveElementWalkingVisitor {

    private final PsiVariable variable;
    private final ClassType collectionType;
    private boolean valueOfInterestAdded;

    ValueAddedVisitor(PsiVariable variable,
                      ClassType collectionType) {
      this.variable = variable;
      this.collectionType = collectionType;
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      if (valueOfInterestAdded) {
        return;
      }
      if (ClassType.OTHER == collectionType) {
        return;
      }

      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final PsiExpression qualifierExpression =
        methodExpression.getQualifierExpression();
      if (!(qualifierExpression instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)qualifierExpression;
      @NonNls final String methodName =
        methodExpression.getReferenceName();

      if (ClassType.SET == collectionType && !SET_METHOD_NAMES.contains(methodName)) {
        return;
      }
      if (ClassType.MAP == collectionType && !MAP_METHOD_NAMES.contains(methodName)) {
        return;
      }

      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();

      if (arguments.length < 1 || arguments[0] == null) {
        return;
      }

      final PsiExpression argument = arguments[0];
      final PsiType argumentType = argument.getType();

      if (shouldTriggerOnKeyType(argumentType)) {
        final PsiElement element = referenceExpression.resolve();
        if (!variable.equals(element)) {
          return;
        }
        valueOfInterestAdded = true;
      }
      else if (methodName.endsWith("All")) {
        PsiClass className = PsiUtil.resolveClassInClassTypeOnly(argumentType);
        PsiType[] parameterList = ((PsiClassType)argumentType).getParameters();
        if (parameterList.length == 0) {
          return;
        }
        ClassType argumentClassType = isMapOrSet(className, new HashSet<>());
        if (ClassType.MAP == argumentClassType || ClassType.SET == argumentClassType) {
          if (parameterList[0] != null) {
            PsiType keyType = parameterList[0];
            if (shouldTriggerOnKeyType(keyType)) {
              final PsiElement element = referenceExpression.resolve();
              if (!variable.equals(element)) {
                return;
              }
              valueOfInterestAdded = true;
            }
          }
        }
      }
    }

    boolean isValueOfInterestAdded() {
      return valueOfInterestAdded;
    }
  }
}
