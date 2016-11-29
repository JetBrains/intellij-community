/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.introduceParameterObject;

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.changeSignature.ChangeInfo;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import com.intellij.refactoring.introduceParameterObject.IntroduceParameterObjectClassDescriptor;
import com.intellij.refactoring.introduceParameterObject.IntroduceParameterObjectDelegate;
import com.intellij.refactoring.introduceparameterobject.JavaIntroduceParameterObjectDelegate;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.refactoring.changeSignature.GrChangeInfoImpl;
import org.jetbrains.plugins.groovy.refactoring.changeSignature.GrMethodDescriptor;
import org.jetbrains.plugins.groovy.refactoring.changeSignature.GrParameterInfo;

import java.util.Collection;
import java.util.List;

public class GroovyIntroduceParameterObjectDelegate
  extends IntroduceParameterObjectDelegate<GrMethod, GrParameterInfo, GroovyIntroduceObjectClassDescriptor> {
  @Override
  public boolean isEnabledOn(PsiElement element) {
    return false;
  }

  @Override
  public RefactoringActionHandler getHandler(PsiElement element) {
    return null;
  }

  @Override
  public List<GrParameterInfo> getAllMethodParameters(GrMethod sourceMethod) {
    return new GrMethodDescriptor(sourceMethod).getParameters();
  }

  @Override
  public GrParameterInfo createMergedParameterInfo(GroovyIntroduceObjectClassDescriptor descriptor,
                                                   GrMethod method,
                                                   List<GrParameterInfo> oldMethodParameters) {
    final GroovyPsiElementFactory elementFactory = GroovyPsiElementFactory.getInstance(method.getProject());
    PsiType classType =
      elementFactory.createTypeByFQClassName(StringUtil.getQualifiedName(descriptor.getPackageName(), descriptor.getClassName()));
    return new GrParameterInfo(descriptor.getClassName(), null, null, classType, -1, false) {
      @Nullable
      @Override
      public PsiElement getActualValue(PsiElement callExpression, Object substitutor) {
        final IntroduceParameterObjectDelegate<PsiNamedElement, ParameterInfo, IntroduceParameterObjectClassDescriptor<PsiNamedElement, ParameterInfo>>
          delegate = findDelegate(callExpression);
        return delegate != null ? delegate.createNewParameterInitializerAtCallSite(callExpression, descriptor, oldMethodParameters, substitutor) : null;
      }
    };
  }

  @Override
  public PsiElement createNewParameterInitializerAtCallSite(PsiElement callExpression,
                                                            IntroduceParameterObjectClassDescriptor descriptor,
                                                            List<? extends ParameterInfo> oldMethodParameters,
                                                            Object substitutor) {
    if (callExpression instanceof GrCallExpression) {
      final GrArgumentList list = ((GrCallExpression)callExpression).getArgumentList();
      if (list == null) {
        return null;
      }
      final GrExpression[] args = list.getExpressionArguments();

      final String qualifiedName = StringUtil.getQualifiedName(descriptor.getPackageName(), descriptor.getClassName());

      String newExpression =
        "new " + qualifiedName + '(' + JavaIntroduceParameterObjectDelegate.getMergedArgs(descriptor, oldMethodParameters, args) + ')';

      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(callExpression.getProject());
      return factory.createExpressionFromText(newExpression, callExpression);
    }
    return null;
  }

  @Override
  public ChangeInfo createChangeSignatureInfo(GrMethod method, List<GrParameterInfo> newParameterInfos, boolean delegate) {
    final PsiType returnType = method.getReturnType();
    return new GrChangeInfoImpl(method,
                                VisibilityUtil.getVisibilityModifier(method.getModifierList()),
                                returnType != null ? CanonicalTypes.createTypeWrapper(returnType) : null,
                                method.getName(),
                                newParameterInfos,
                                null,
                                delegate);
  }

  @Override
  public <M1 extends PsiNamedElement, P1 extends ParameterInfo> ReadWriteAccessDetector.Access collectInternalUsages(Collection<FixableUsageInfo> usages,
                                                                                                                     GrMethod overridingMethod,
                                                                                                                     IntroduceParameterObjectClassDescriptor<M1, P1> classDescriptor,
                                                                                                                     P1 parameterInfo,
                                                                                                                     String mergedParamName) {
    final int oldIndex = parameterInfo.getOldIndex();
    final GrParameter parameter = overridingMethod.getParameterList().getParameters()[oldIndex];
    final ReadWriteAccessDetector.Access[] accessors = new ReadWriteAccessDetector.Access[1];
    final String setter = classDescriptor.getSetterName(parameterInfo, overridingMethod);
    final String getter = classDescriptor.getGetterName(parameterInfo, overridingMethod);
    ReferencesSearch.search(parameter, new LocalSearchScope(overridingMethod)).forEach(reference -> {
      final PsiElement element = reference.getElement();
      if (element instanceof GrReferenceExpression) {
        accessors[0] = ReadWriteAccessDetector.Access.Read;
        //todo proceed with write access
        usages.add(new GrReplaceParameterReferenceWithCall(element, getter, mergedParamName));
      }
      return true;
    });
    return accessors[0];
  }

  @Override
  public void collectUsagesToGenerateMissedFieldAccessors(Collection<FixableUsageInfo> usages,
                                                          GrMethod method,
                                                          GroovyIntroduceObjectClassDescriptor descriptor,
                                                          ReadWriteAccessDetector.Access[] accessors) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void collectAdditionalFixes(Collection<FixableUsageInfo> usages,
                                     GrMethod method,
                                     GroovyIntroduceObjectClassDescriptor descriptor) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void collectConflicts(MultiMap<PsiElement, String> conflicts,
                               UsageInfo[] infos,
                               GrMethod method,
                               GroovyIntroduceObjectClassDescriptor classDescriptor) {
    throw new UnsupportedOperationException();
  }
}
