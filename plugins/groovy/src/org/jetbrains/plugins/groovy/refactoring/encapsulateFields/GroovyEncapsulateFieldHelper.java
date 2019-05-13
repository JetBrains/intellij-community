/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.refactoring.encapsulateFields;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.encapsulateFields.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.JavaStylePropertiesUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Max Medvedev
 */
public class GroovyEncapsulateFieldHelper extends EncapsulateFieldHelper {
  private static final Logger LOG = Logger.getInstance(GroovyEncapsulateFieldHelper.class);

  @NotNull
  @Override
  public PsiField[] getApplicableFields(@NotNull PsiClass aClass) {
    if (aClass instanceof GrTypeDefinition) {
      return ((GrTypeDefinition)aClass).getCodeFields();
    }
    else {
      return aClass.getFields();
    }
  }

  @Override
  @NotNull
  public String suggestSetterName(@NotNull PsiField field) {
    return PropertyUtilBase.suggestSetterName(field);
  }

  @Override
  @NotNull
  public String suggestGetterName(@NotNull PsiField field) {
    return PropertyUtilBase.suggestGetterName(field);
  }

  @Override
  @Nullable
  public PsiMethod generateMethodPrototype(@NotNull PsiField field, @NotNull String methodName, boolean isGetter) {
    PsiMethod prototype = isGetter
                          ? GroovyPropertyUtils.generateGetterPrototype(field)
                          : GroovyPropertyUtils.generateSetterPrototype(field);

    try {
      prototype.setName(methodName);
      return prototype;
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  @Override
  @Nullable
  public EncapsulateFieldUsageInfo createUsage(@NotNull EncapsulateFieldsDescriptor descriptor,
                                               @NotNull FieldDescriptor fieldDescriptor,
                                               @NotNull PsiReference reference) {
    if (!(reference instanceof GrReferenceExpression)) return null;

    boolean findSet = descriptor.isToEncapsulateSet();
    boolean findGet = descriptor.isToEncapsulateGet();
    GrReferenceExpression ref = (GrReferenceExpression)reference;
    if (findGet &&
        JavaEncapsulateFieldHelper.isUsedInExistingAccessor(descriptor.getTargetClass(), fieldDescriptor.getGetterPrototype(), ref)) {
      return null;
    }
    if (findSet &&
        JavaEncapsulateFieldHelper.isUsedInExistingAccessor(descriptor.getTargetClass(), fieldDescriptor.getSetterPrototype(), ref)) {
      return null;
    }
    if (!findGet) {
      if (!PsiUtil.isAccessedForWriting(ref)) return null;
    }
    if (!findSet || fieldDescriptor.getField().hasModifierProperty(PsiModifier.FINAL)) {
      if (!PsiUtil.isAccessedForReading(ref)) return null;
    }
    /*if (!descriptor.isToUseAccessorsWhenAccessible()) {
      PsiModifierList newModifierList = JavaEncapsulateFieldHelper.createNewModifierList(descriptor);
      PsiClass accessObjectClass = getAccessObject(ref);
      final PsiResolveHelper helper = JavaPsiFacade.getInstance(((GrReferenceExpression)reference).getProject()).getResolveHelper();
      if (helper.isAccessible(fieldDescriptor.getField(), newModifierList, ref, accessObjectClass, null)) {
        return null;
      }
    }*/
    return new EncapsulateFieldUsageInfo(ref, fieldDescriptor);
  }

  @Nullable
  private static PsiClass getAccessObject(@NotNull GrReferenceExpression ref) {
    GrExpression qualifier = ref.getQualifierExpression();
    if (qualifier != null) {
      return (PsiClass)PsiUtil.getAccessObjectClass(qualifier).getElement();
    }
    return null;
  }

  @Override
  public boolean processUsage(@NotNull EncapsulateFieldUsageInfo usage,
                              @NotNull EncapsulateFieldsDescriptor descriptor,
                              PsiMethod setter,
                              PsiMethod getter) {
    final PsiElement element = usage.getElement();
    if (!(element instanceof GrReferenceExpression)) return false;

    final FieldDescriptor fieldDescriptor = usage.getFieldDescriptor();
    PsiField field = fieldDescriptor.getField();
    boolean processGet = descriptor.isToEncapsulateGet();
    boolean processSet = descriptor.isToEncapsulateSet() && !field.hasModifierProperty(PsiModifier.FINAL);
    if (!processGet && !processSet) return true;

    final GrReferenceExpression expr = (GrReferenceExpression)element;

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(descriptor.getTargetClass().getProject());

    try {
      if (!descriptor.isToUseAccessorsWhenAccessible()) {
        PsiModifierList newModifierList = JavaEncapsulateFieldHelper.createNewModifierList(descriptor);
        PsiClass accessObjectClass = getAccessObject(expr);
        final PsiResolveHelper helper = JavaPsiFacade.getInstance((expr).getProject()).getResolveHelper();
        if (helper.isAccessible(fieldDescriptor.getField(), newModifierList, expr, accessObjectClass, null)) {
          GroovyResolveResult[] results = expr.multiResolve(false);
          if (ContainerUtil.or(results, it -> it.isValidResult() && it.getElement() instanceof PsiMethod)) {
            addMemberOperator(expr, field);
          }
          return true;
        }
      }

      final PsiElement parent = expr.getParent();
      if (parent instanceof GrAssignmentExpression && expr.equals(((GrAssignmentExpression)parent).getLValue())) {
        GrAssignmentExpression assignment = (GrAssignmentExpression)parent;
        if (assignment.getRValue() != null) {
          PsiElement opSign = assignment.getOperationToken();
          if (!assignment.isOperatorAssignment()) {
            if (!processSet || (checkSetterIsSimple(field, setter) && checkFieldIsInaccessible(field, expr))) return true;


            final GrExpression setterArgument = assignment.getRValue();

            GrMethodCallExpression methodCall = createSetterCall(fieldDescriptor, setterArgument, expr, descriptor.getTargetClass(), setter);

            if (methodCall != null) {
              tryToSimplify((GrMethodCallExpression)assignment.replaceWithExpression(methodCall, true));
            }
            //TODO: check if value is used!!!
          }
          else {
            // Q: side effects of qualifier??!
            if (checkAccessorsAreSimpleAndFieldIsInaccessible(field, setter, getter, expr)) {
              return true;
            }
            String opName = opSign.getText();
            LOG.assertTrue(StringUtil.endsWithChar(opName, '='));
            opName = opName.substring(0, opName.length() - 1);

            GrExpression getExpr = expr;
            if (processGet) {
              final GrExpression getterCall = createGetterCall(fieldDescriptor, expr, descriptor.getTargetClass(), getter);
              if (getterCall != null) {
                getExpr = getterCall;
              }
            }

            @NonNls String text = "a" + opName + "b";
            GrBinaryExpression binExpr = (GrBinaryExpression)factory.createExpressionFromText(text, expr);
            tryToSimplify((GrMethodCallExpression)binExpr.getLeftOperand().replaceWithExpression(getExpr, true));
            binExpr.getRightOperand().replaceWithExpression(assignment.getRValue(), true);

            GrExpression setExpr;
            if (processSet) {
              setExpr = createSetterCall(fieldDescriptor, binExpr, expr, descriptor.getTargetClass(), setter);
            }
            else {
              text = "a = b";
              GrAssignmentExpression newAssignment = (GrAssignmentExpression)factory.createExpressionFromText(text, null);
              newAssignment.getLValue().replaceWithExpression(expr, true);
              newAssignment.getRValue().replaceWithExpression(binExpr, true);
              setExpr = newAssignment;
            }

            tryToSimplify((GrMethodCallExpression)assignment.replaceWithExpression(setExpr, true));
            //TODO: check if value is used!!!
          }
        }
      }
      else if (parent instanceof GrUnaryExpression &&
               (((GrUnaryExpression)parent).getOperationTokenType() == GroovyTokenTypes.mINC ||
                ((GrUnaryExpression)parent).getOperationTokenType() == GroovyTokenTypes.mDEC)) {
        if (checkAccessorsAreSimpleAndFieldIsInaccessible(field, setter, getter, expr)) {
          return true;
        }
        IElementType sign = ((GrUnaryExpression)parent).getOperationTokenType();

        GrExpression getExpr = expr;
        if (processGet) {
          final GrExpression getterCall = createGetterCall(fieldDescriptor, expr, descriptor.getTargetClass(), getter);
          if (getterCall != null) {
            getExpr = getterCall;
          }
        }

        @NonNls String text = sign == GroovyTokenTypes.mINC
                              ? "a+1"
                              : "a-1";
        GrBinaryExpression binExpr = (GrBinaryExpression)factory.createExpressionFromText(text, parent);
        tryToSimplify((GrMethodCallExpression)binExpr.getLeftOperand().replaceWithExpression(getExpr, true));

        GrExpression setExpr;
        if (processSet) {
          setExpr = createSetterCall(fieldDescriptor, binExpr, expr, descriptor.getTargetClass(), setter);
        }
        else {
          text = "a = b";
          GrAssignmentExpression assignment = (GrAssignmentExpression)factory.createExpressionFromText(text, null);
          assignment.getLValue().replaceWithExpression(expr, true);
          assignment.getRValue().replaceWithExpression(binExpr, true);
          setExpr = assignment;
        }
        tryToSimplify((GrMethodCallExpression)((GrUnaryExpression)parent).replaceWithExpression(setExpr, true));
      }
      else {
        if (!processGet || (checkGetterIsSimple(field, getter) && checkFieldIsInaccessible(field, expr))) return true;
        GrExpression methodCall = createGetterCall(fieldDescriptor, expr, descriptor.getTargetClass(), getter);

        if (methodCall != null) {
          tryToSimplify(((GrMethodCallExpression)expr.replaceWithExpression(methodCall, true)));
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return true;
  }

  private static void addMemberOperator(@NotNull GrReferenceExpression ref, @NotNull PsiField field) {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(ref.getProject());

    GrExpression qualifier = ref.getQualifier();
    if (qualifier == null) {
      PsiClass parentClass = PsiTreeUtil.getParentOfType(ref, PsiClass.class);
      PsiClass containingClass = findContainingClass(ref, field);
      GrReferenceExpression thisRef = !ref.getManager().areElementsEquivalent(parentClass, containingClass)
                                      ? factory.createThisExpression(containingClass)
                                      : factory.createThisExpression(null);
      ref.setQualifier(thisRef);
    }

    ref.getNode().addLeaf(GroovyTokenTypes.mAT, "@", ref.getDotToken().getNode().getTreeNext());
  }

  private static PsiClass findContainingClass(@NotNull GrReferenceExpression ref, @NotNull PsiField field) {
    PsiElement context = ref;
    while (true) {
      PsiClass aClass = PsiUtil.getContextClass(context);
      if (aClass == null) return null;

      PsiManager manager = context.getManager();
      PsiField found = aClass.findFieldByName(field.getName(), true);
      if (manager.areElementsEquivalent(found, field)) {
        return aClass;
      }

      if (context instanceof GroovyScriptClass) return null;
      if (context.getParent() instanceof GroovyFile) return null;

      context = aClass.getParent();
    }
  }

  private static boolean checkAccessorsAreSimpleAndFieldIsInaccessible(@NotNull PsiField field,
                                                                       @Nullable PsiMethod setter,
                                                                       @Nullable PsiMethod getter,
                                                                       @NotNull GrReferenceExpression place) {
    return (setter == null || checkSetterIsSimple(field, setter)) &&
           (getter == null || checkGetterIsSimple(field, getter)) &&
           checkFieldIsInaccessible(field, place);
  }

  private static boolean checkSetterIsSimple(@NotNull PsiField field, @NotNull PsiMethod setter) {
    final String nameBySetter = GroovyPropertyUtils.getPropertyNameBySetterName(setter.getName());
    return field.getName().equals(nameBySetter);
  }

  private static boolean checkGetterIsSimple(@NotNull PsiField field, @NotNull PsiMethod getter) {
    final String nameByGetter = GroovyPropertyUtils.getPropertyNameByGetterName(getter.getName(), true);
    return field.getName().equals(nameByGetter);
  }

  private static boolean checkFieldIsInaccessible(PsiField field, @NotNull GrReferenceExpression place) {
    PsiResolveHelper helper = JavaPsiFacade.getInstance(field.getProject()).getResolveHelper();
    return helper.isAccessible(field, place, getAccessObject(place));
  }

  private static void tryToSimplify(@NotNull GrMethodCallExpression methodCall) {
    if (JavaStylePropertiesUtil.isPropertyAccessor(methodCall)) {
      JavaStylePropertiesUtil.fixJavaStyleProperty(methodCall);
    }
  }

  private static GrMethodCallExpression createSetterCall(FieldDescriptor fieldDescriptor,
                                                         GrExpression setterArgument,
                                                         GrReferenceExpression expr,
                                                         PsiClass aClass,
                                                         PsiMethod setter) throws IncorrectOperationException {
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(fieldDescriptor.getField().getProject());
    final String setterName = fieldDescriptor.getSetterName();
    @NonNls String text = setterName + "(a)";
    GrExpression qualifier = expr.getQualifierExpression();
    if (qualifier != null) {
      text = "q." + text;
    }
    GrMethodCallExpression methodCall = (GrMethodCallExpression)factory.createExpressionFromText(text, expr);

    methodCall.getArgumentList().getExpressionArguments()[0].replace(setterArgument);
    if (qualifier != null) {
      ((GrReferenceExpression)methodCall.getInvokedExpression()).getQualifierExpression().replace(qualifier);
    }
    methodCall = checkMethodResolvable(methodCall, setter, expr, aClass);
    if (methodCall == null) {
      VisibilityUtil.escalateVisibility(fieldDescriptor.getField(), expr);
    }
    return methodCall;
  }

  @Nullable
  private static GrMethodCallExpression createGetterCall(FieldDescriptor fieldDescriptor,
                                                         GrReferenceExpression expr,
                                                         PsiClass aClass,
                                                         PsiMethod getter) throws IncorrectOperationException {
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(fieldDescriptor.getField().getProject());
    final String getterName = fieldDescriptor.getGetterName();
    @NonNls String text = getterName + "()";
    GrExpression qualifier = expr.getQualifierExpression();
    if (qualifier != null) {
      text = "q." + text;
    }
    GrMethodCallExpression methodCall = (GrMethodCallExpression)factory.createExpressionFromText(text, expr);

    if (qualifier != null) {
      ((GrReferenceExpression)methodCall.getInvokedExpression()).getQualifierExpression().replace(qualifier);
    }

    methodCall = checkMethodResolvable(methodCall, getter, expr, aClass);
    if (methodCall == null) {
      VisibilityUtil.escalateVisibility(fieldDescriptor.getField(), expr);
    }
    return methodCall;
  }

  @Nullable
  private static GrMethodCallExpression checkMethodResolvable(GrMethodCallExpression methodCall,
                                                              PsiMethod targetMethod,
                                                              GrReferenceExpression context,
                                                              PsiClass aClass) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(targetMethod.getProject()).getElementFactory();
    final PsiElement resolved = ((GrReferenceExpression)methodCall.getInvokedExpression()).resolve();
    if (resolved != targetMethod) {
      PsiClass containingClass;
      if (resolved instanceof PsiMethod) {
        containingClass = ((PsiMethod)resolved).getContainingClass();
      }
      else if (resolved instanceof PsiClass) {
        containingClass = (PsiClass)resolved;
      }
      else {
        return null;
      }
      if (containingClass != null && containingClass.isInheritor(aClass, false)) {
        final PsiExpression newMethodExpression =
          factory.createExpressionFromText("super." + targetMethod.getName(), context);
        methodCall.getInvokedExpression().replace(newMethodExpression);
      }
      else {
        methodCall = null;
      }
    }
    return methodCall;
  }
}
