/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.completion.closureParameters.ClosureParameterInfo;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.ClosureParameterEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.Arrays;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class EachWithIndexClosureCompleter extends ClosureCompleter {
  @Nullable
  @Override
  protected List<ClosureParameterInfo> getParameterInfos(InsertionContext context,
                                                         PsiMethod method,
                                                         PsiSubstitutor substitutor,
                                                         PsiElement place) {
    final String name = method.getName();
    if (!"eachWithIndex".equals(name)) return null;

    if (method instanceof GrGdkMethod) method = ((GrGdkMethod)method).getStaticMethod();

    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return null;

    final String qname = containingClass.getQualifiedName();

    if (!GroovyCommonClassNames.DEFAULT_GROOVY_METHODS.equals(qname)) return null;

    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 2) return null;

    final PsiType type = parameters[0].getType();
    final PsiType collection = substitutor.substitute(type);

    final PsiType iterable = getIteratedType(place, collection);
    if (iterable != null) {
      return Arrays.asList(
        new ClosureParameterInfo(iterable.getCanonicalText(), "entry"),
        new ClosureParameterInfo("int", "i")
      );
    }

    if (InheritanceUtil.isInheritor(collection, CommonClassNames.JAVA_UTIL_MAP)) {
      final PsiType[] typeParams = ((PsiClassType)collection).getParameters();

      final Project project = context.getProject();

      final PsiClass entry = JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_UTIL_MAP_ENTRY, place.getResolveScope());
      if (entry == null) return null;

      final PsiClassType entryType = JavaPsiFacade.getElementFactory(project).createType(entry, typeParams);


      return Arrays.asList(new ClosureParameterInfo(entryType.getCanonicalText(), "entry"), new ClosureParameterInfo("int", "i"));
    }

    return Arrays.asList(new ClosureParameterInfo(collection.getCanonicalText(), "entry"), new ClosureParameterInfo("int", "i"));
  }

  private static PsiType getIteratedType(PsiElement parent, PsiType collection) {
    if (parent instanceof GrReferenceExpression) {
      final GrExpression qualifier = ((GrReferenceExpression)parent).getQualifier();
      if (qualifier != null) {
        return ClosureParameterEnhancer.findTypeForIteration(qualifier, parent);
      }
    }

    final PsiType iterable = PsiUtil.extractIterableTypeParameter(collection, true);
    if (iterable != null && parent instanceof GrExpression) {
      return PsiImplUtil.normalizeWildcardTypeByPosition(iterable, (GrExpression)parent);
    }
    else {
      return iterable;
    }
  }
}
