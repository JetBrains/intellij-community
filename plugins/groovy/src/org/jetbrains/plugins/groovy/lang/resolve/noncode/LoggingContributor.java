/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve.noncode;

import com.google.common.collect.ImmutableMap;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyConstantExpressionEvaluator;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author peter
 */
public class LoggingContributor extends NonCodeMembersContributor {
  private static final ImmutableMap<String, String> ourLoggers = ImmutableMap.<String, String>builder().
    put("groovy.util.logging.Log", "java.util.logging.Logger").
    put("groovy.util.logging.Commons", "org.apache.commons.logging.Log").
    put("groovy.util.logging.Log4j", "org.apache.log4j.Logger").
    put("groovy.util.logging.Slf4j", "org.slf4j.Logger").
    build();

  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType, PsiScopeProcessor processor, GroovyPsiElement place, ResolveState state) {
    if (!(qualifierType instanceof PsiClassType)) return;

    PsiClass psiClass = ((PsiClassType)qualifierType).resolve();
    if (!(psiClass instanceof GrTypeDefinition) || !PsiTreeUtil.isAncestor(psiClass, place, true)) return;

    PsiModifierList modifierList = psiClass.getModifierList();
    if (modifierList == null) return;
    for (PsiAnnotation annotation : modifierList.getAnnotations()) {
      String qname = annotation.getQualifiedName();
      String logger = ourLoggers.get(qname);
      if (logger != null) {
        String fieldName = "log";
        PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("value");
        if (value instanceof GrExpression) {
          Object o = GroovyConstantExpressionEvaluator.evaluate((GrExpression)value);
          if (o instanceof String) {
            fieldName = (String)o;
          }
        }
        LightFieldBuilder field = new LightFieldBuilder(fieldName, logger, annotation).setContainingClass(psiClass)
          .setModifiers(PsiModifier.FINAL, PsiModifier.STATIC, PsiModifier.PRIVATE);
        ResolveUtil.processElement(processor, field, state);
      }
    }
  }

}
