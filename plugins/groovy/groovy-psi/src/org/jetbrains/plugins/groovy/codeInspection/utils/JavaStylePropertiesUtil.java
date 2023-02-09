// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.utils;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author Max Medvedev
 */
public final class JavaStylePropertiesUtil {
  private static final Logger LOG = Logger.getInstance(JavaStylePropertiesUtil.class);

  public static void fixJavaStyleProperty(GrMethodCall call) {
    GrExpression invoked = call.getInvokedExpression();
    String accessorName = ((GrReferenceExpression)invoked).getReferenceName();
    if (isGetterInvocation(call)) {
      final GrExpression newCall = genRefForGetter(call, accessorName);
      call.replaceWithExpression(newCall, true);
    }
    else if (isSetterInvocation(call)) {
      final GrStatement newCall = genRefForSetter(call, accessorName);
      if(newCall != null) {
        call.replaceWithStatement(newCall);
      }
    }
  }

  public static boolean isPropertyAccessor(GrMethodCall call) {
    return !isInvokedOnMap(call) && (isGetterInvocation(call) || isSetterInvocation(call));
  }

  @Nullable
  private static GrAssignmentExpression genRefForSetter(GrMethodCall call, String accessorName) {
    String name = GroovyPropertyUtils.getPropertyNameBySetterName(accessorName);
    if(name == null) return null;
    GrExpression value = call.getExpressionArguments()[0];
    GrReferenceExpression refExpr = (GrReferenceExpression)call.getInvokedExpression();

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(call.getProject());
    final GrAssignmentExpression assignment = (GrAssignmentExpression)factory.createStatementFromText("yyy = xxx", call);

    GrReferenceExpression lvalueRef = (GrReferenceExpression)assignment.getLValue();
    lvalueRef.setQualifier(refExpr.getQualifier());
    lvalueRef.handleElementRename(name);
    assignment.getRValue().replaceWithExpression(value, true);

    return assignment;
  }

  private static GrExpression genRefForGetter(GrMethodCall call, String accessorName) {
    String name = GroovyPropertyUtils.getPropertyNameByGetterName(accessorName, true);
    GrReferenceExpression refExpr = (GrReferenceExpression)call.getInvokedExpression();
    String oldNameStr = refExpr.getReferenceNameElement().getText();
    String newRefExpr = StringUtil.trimEnd(refExpr.getText(), oldNameStr) + name;

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(call.getProject());
    return factory.createExpressionFromText(newRefExpr, call);
  }

  private static boolean isInvokedOnMap(GrMethodCall call) {
    GrExpression expr = call.getInvokedExpression();
    return expr instanceof GrReferenceExpression && ResolveUtil.isKeyOfMap((GrReferenceExpression)expr);
  }

  private static boolean isSetterInvocation(GrMethodCall call) {
    GrExpression expr = call.getInvokedExpression();

    if (!(expr instanceof GrReferenceExpression refExpr)) return false;

    PsiMethod method;
    if (call instanceof GrApplicationStatement) {
      PsiElement element = refExpr.resolve();
      if (!(element instanceof PsiMethod) || !GroovyPropertyUtils.isSimplePropertySetter(((PsiMethod)element))) return false;
      method = (PsiMethod)element;
    }
    else {
      method = call.resolveMethod();
      if (!GroovyPropertyUtils.isSimplePropertySetter(method)) return false;
    }

    if (!GroovyNamesUtil.isValidReference(GroovyPropertyUtils.getPropertyNameBySetterName(method.getName()),
                                          ((GrReferenceExpression)expr).getQualifier() != null,
                                          call.getProject())) {
      return false;
    }

    GrArgumentList args = call.getArgumentList();
    if (args.getExpressionArguments().length != 1 || PsiImplUtil.hasNamedArguments(args)) {
      return false;
    }

    GrAssignmentExpression assignment = genRefForSetter(call, refExpr.getReferenceName());
    if(assignment != null) {
      GrExpression value = assignment.getLValue();
      if (value instanceof GrReferenceExpression &&
          call.getManager().areElementsEquivalent(((GrReferenceExpression)value).resolve(), method)) {
        return true;
      }
    }

    return false;
  }

  private static boolean isGetterInvocation(@NotNull GrMethodCall call) {
    GrExpression expr = call.getInvokedExpression();
    if (!(expr instanceof GrReferenceExpression)) return false;

    PsiMethod method = call.resolveMethod();
    if (!GroovyPropertyUtils.isSimplePropertyGetter(method)) return false;
    if (!GroovyNamesUtil.isValidReference(GroovyPropertyUtils.getPropertyNameByGetterName(method.getName(), true),
                                          ((GrReferenceExpression)expr).getQualifier() != null,
                                          call.getProject())) {
      return false;
    }

    GrArgumentList args = call.getArgumentList();
    if (args.getAllArguments().length != 0) {
      return false;
    }

    GrExpression ref = genRefForGetter(call, ((GrReferenceExpression)expr).getReferenceName());
    if (ref instanceof GrReferenceExpression) {
      PsiElement resolved = ((GrReferenceExpression)ref).resolve();
      PsiManager manager = call.getManager();
      if (manager.areElementsEquivalent(resolved, method) || areEquivalentAccessors(method, resolved, manager)) {
        return true;
      }
    }

    return false;
  }

  private static boolean areEquivalentAccessors(PsiMethod method, PsiElement resolved, PsiManager manager) {
    if (!(resolved instanceof GrAccessorMethod) || !(method instanceof GrAccessorMethod)) {
      return false;
    }

    if (((GrAccessorMethod)resolved).isSetter() != ((GrAccessorMethod)method).isSetter()) return false;

    GrField p1 = ((GrAccessorMethod)resolved).getProperty();
    GrField p2 = ((GrAccessorMethod)method).getProperty();
    return manager.areElementsEquivalent(p1, p2);
  }
}
