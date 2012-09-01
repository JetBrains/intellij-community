/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess;

import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.annotator.GrHighlightUtil;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.gpp.GppTypeConverter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.Iterator;
import java.util.List;

import static org.jetbrains.plugins.groovy.annotator.GrHighlightUtil.isDeclarationAssignment;

/**
 * @author Maxim.Medvedev
 */
public class GroovyUnresolvedAccessInspection extends BaseInspection {
  protected BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return PROBABLE_BUGS;
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Access to unresolved expression";
  }


  @Override
  protected String buildErrorString(Object... args) {
    return "Can not resolve symbol '#ref'";
  }

  private static class Visitor extends BaseInspectionVisitor {
    @Override
    public void visitReferenceExpression(GrReferenceExpression refExpr) {
      super.visitReferenceExpression(refExpr);

      PsiElement resolved = refExpr.advancedResolve().getElement();
      if (resolved != null) return;

      GrExpression qualifier = refExpr.getQualifierExpression();
      if (qualifier == null && isDeclarationAssignment(refExpr)) return;

      PsiElement parent = refExpr.getParent();
      if (!(parent instanceof GrCall) && ResolveUtil.isKeyOfMap(refExpr)) return; // It's a key of map.

      if (!GrHighlightUtil.shouldHighlightAsUnresolved(refExpr)) return;
      
      if (qualifier != null && isBuilderInvocation(refExpr)) return;

      PsiElement refNameElement = refExpr.getReferenceNameElement();
      registerError(refNameElement == null ? refExpr : refNameElement);
    }

  }
  private static boolean isBuilderInvocation(@NotNull GrReferenceExpression refExpr) {
    GrExpression qualifier = refExpr.getQualifier();
    PsiType type = qualifier == null ? null : qualifier.getType();
    if (type instanceof PsiClassType) {
      PsiClass target = ((PsiClassType)type).resolve();
      if (target != null) {
        for (PsiMethod method : findBuilderMetaMethods(refExpr, target)) {
          PsiClass containingClass = method.getContainingClass();
          if (containingClass != null &&
              method.getParameterList().getParameters()[0].getType().equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
            String qname = containingClass.getQualifiedName();
            if (!GroovyCommonClassNames.GROOVY_OBJECT.equals(qname) && !GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT.equals(qname)) {
              return true;
            }
          }
        }
      }
    }

    return false;
  }

  private static List<PsiMethod> findBuilderMetaMethods(GrReferenceExpression refExpr, PsiClass target) {
    boolean gpp = GppTypeConverter.hasTypedContext(target) && GppTypeConverter.hasTypedContext(refExpr);
    if (refExpr.getParent() instanceof GrCall) {
      List<PsiMethod> toSearch =
        ContainerUtil.newArrayList(target.findMethodsByName(gpp ? "invokeUnresolvedMethod" : "invokeMethod", true));
      for (Iterator<PsiMethod> iterator = toSearch.iterator(); iterator.hasNext(); ) {
        PsiMethod method = iterator.next();
        if (!gpp &&
            (method.getParameterList().getParametersCount() != 2 || method.getParameterList().getParameters()[1].getType()
          .equalsToText(CommonClassNames.JAVA_LANG_OBJECT + "[]"))) {
          iterator.remove();
        }
      }
      return toSearch;
    }

    if (PsiUtil.isLValue(refExpr)) {
      List<PsiMethod> toSearch = ContainerUtil.newArrayList(target.findMethodsByName(gpp ? "setUnresolvedProperty" : "setProperty", true));
      for (Iterator<PsiMethod> iterator = toSearch.iterator(); iterator.hasNext(); ) {
        PsiMethod method = iterator.next();
        if (method.getParameterList().getParametersCount() != 2 || (!gpp && !method.getParameterList().getParameters()[1].getType()
          .equalsToText(CommonClassNames.JAVA_LANG_OBJECT))) {
          iterator.remove();
        }
      }
      return toSearch;
    }

    List<PsiMethod> toSearch = ContainerUtil.newArrayList(target.findMethodsByName(gpp ? "getUnresolvedProperty" : "getProperty", true));
    for (Iterator<PsiMethod> iterator = toSearch.iterator(); iterator.hasNext(); ) {
      if (iterator.next().getParameterList().getParametersCount() != 1) {
        iterator.remove();
      }
    }
    return toSearch;
  }
}
