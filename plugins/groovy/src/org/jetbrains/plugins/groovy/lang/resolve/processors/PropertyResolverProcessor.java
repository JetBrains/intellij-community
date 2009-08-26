/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrThisReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.Set;

/**
 * @author ven
 */
public class PropertyResolverProcessor extends ResolverProcessor {

  public PropertyResolverProcessor(String name, PsiElement place) {
    super(name, EnumSet.of(ResolveKind.METHOD, ResolveKind.PROPERTY), place, PsiType.EMPTY_ARRAY);
  }

  public boolean execute(PsiElement element, ResolveState state) {
    if (myName != null && element instanceof PsiMethod && !(element instanceof GrAccessorMethod) && myPlace instanceof GroovyPsiElement) {
      PsiMethod method = (PsiMethod) element;
      boolean lValue = PsiUtil.isLValue((GroovyPsiElement) myPlace);
      if (!lValue && GroovyPropertyUtils.isSimplePropertyGetter(method, myName) ||
          lValue && GroovyPropertyUtils.isSimplePropertySetter(method, myName)) {
        if (method instanceof GrMethod && isFieldReferenceInSameClass(method, myName)) {
          return true;
        }

        super.execute(element, state);
      }
      return true;
    }

    if (myName == null || myName.equals(((PsiNamedElement) element).getName())) {
      if (element instanceof GrField && ((GrField) element).isProperty()) {
        boolean isAccessible = isAccessible((PsiNamedElement) element);
        boolean isStaticsOK = isStaticsOK((PsiNamedElement) element);
        myCandidates.add(new GroovyResolveResultImpl(element, myCurrentFileResolveContext, state.get(PsiSubstitutor.KEY), isAccessible, isStaticsOK));
        return true;
      }
      return super.execute(element, state);
    }

    return true;
  }

  private boolean isFieldReferenceInSameClass(final PsiMethod method, final String fieldName) {
    if (!(myPlace instanceof GrReferenceExpression)) {
      return false;
    }

    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null || !PsiTreeUtil.isAncestor(containingClass, myPlace, true)) return false;
    final GrExpression qualifier = ((GrReferenceExpression)myPlace).getQualifierExpression();
    if (qualifier != null && !(qualifier instanceof GrThisReferenceExpression)) {
      return false;
    }

    return containingClass.findFieldByName(fieldName, false) != null;
  }

  public GroovyResolveResult[] getCandidates() {
    final Set<String> propertyNames = new THashSet<String>();
    for (GroovyResolveResult candidate : myCandidates) {
      final PsiElement element = candidate.getElement();
      if (element instanceof PsiMethod) {
        ContainerUtil.addIfNotNull(GroovyPropertyUtils.getPropertyName((PsiMethod)element), propertyNames);
      }
    }

    for (Iterator<GroovyResolveResult> iterator = myCandidates.iterator(); iterator.hasNext();) {
      GroovyResolveResult result = iterator.next();
      final PsiElement element = result.getElement();
      if (element instanceof PsiField && propertyNames.contains(((PsiField)element).getName())) {
        iterator.remove();
      }
    }

    return super.getCandidates();
  }

  private boolean inSameClass(GrMethod element) {
    if (PsiTreeUtil.getParentOfType(myPlace, GrTypeDefinition.class) != element.getContainingClass() ||
      !(myPlace instanceof GrReferenceExpression)) return false;
    final GrExpression qual = ((GrReferenceExpression)myPlace).getQualifierExpression();
    return qual == null || qual instanceof GrThisReferenceExpression;
  }

  public <T> T getHint(Key<T> hintKey) {
    if (NameHint.KEY == hintKey) {
      //we cannot provide name hint here
      return null;
    }

    return super.getHint(hintKey);
  }

}
