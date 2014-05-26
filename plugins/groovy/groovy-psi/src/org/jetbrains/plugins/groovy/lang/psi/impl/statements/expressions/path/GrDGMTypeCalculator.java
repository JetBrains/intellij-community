/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.CollectionUtil;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrCallExpressionTypeCalculator;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.Set;

/**
 * @author Max Medvedev
 */
public class GrDGMTypeCalculator extends GrCallExpressionTypeCalculator {
  private static final Set<String> mySet = ContainerUtil.newLinkedHashSet();

  static {
    mySet.add("unique");
    mySet.add("findAll");
    mySet.add("grep");
    mySet.add("collectMany");
    mySet.add("split");
    mySet.add("plus");
    mySet.add("intersect");
    mySet.add("leftShift");
  }

  @Override
  protected PsiType calculateReturnType(@NotNull GrMethodCall callExpression, @NotNull PsiMethod resolved) {
    if (resolved instanceof GrGdkMethod) {
      resolved = ((GrGdkMethod)resolved).getStaticMethod();
    }

    final PsiClass containingClass = resolved.getContainingClass();
    if (containingClass == null || !GroovyCommonClassNames.DEFAULT_GROOVY_METHODS.equals(containingClass.getQualifiedName())) return null;

    GrExpression qualifier = getQualifier(callExpression);
    if (qualifier == null) return null;

    String name = resolved.getName();
    if ("find".equals(name)) {
      final PsiType type = qualifier.getType();
      if (type instanceof PsiArrayType) return ((PsiArrayType)type).getComponentType();
    }

    if (isSimilarCollectionReturner(resolved)) {
      PsiType returnType = resolved.getReturnType();
      if (returnType instanceof PsiClassType) {
        PsiClass rr = ((PsiClassType)returnType).resolve();
        if (rr != null && CommonClassNames.JAVA_UTIL_COLLECTION.equals(rr.getQualifiedName())) {
          PsiType type = qualifier.getType();
          PsiType itemType = getItemType(type);
          if ("flatten".equals(name) && itemType!=null) {
            while (true) {
              PsiType iitype = getItemType(itemType);
              if (iitype == null) break;
              itemType = iitype;
            }
          }
          return CollectionUtil.createSimilarCollection(type, callExpression.getProject(), itemType);
        }
      }
    }

    return null;
  }

  @Nullable
  private static PsiType getItemType(PsiType type) {
    if (type instanceof PsiArrayType) {
      return ((PsiArrayType)type).getComponentType();
    }
    else {
      return PsiUtil.extractIterableTypeParameter(type, true);
    }
  }

  private static boolean isSimilarCollectionReturner(PsiMethod resolved) {
    PsiParameter[] params = resolved.getParameterList().getParameters();
    if (params.length == 0) return false;

    if (mySet.contains(resolved.getName())) return true;

    PsiType type = params[0].getType();
    return type instanceof PsiArrayType || GroovyPsiManager.isInheritorCached(type, CommonClassNames.JAVA_UTIL_COLLECTION);
  }

  @Nullable
  private static GrExpression getQualifier(GrMethodCall callExpression) {
    GrExpression invoked = callExpression.getInvokedExpression();
    if (invoked instanceof GrReferenceExpression) {
      return ((GrReferenceExpression)invoked).getQualifier();
    }
    else {
      return null;
    }
  }
}
