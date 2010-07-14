/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author Maxim.Medvedev
 */
public class GroovySmartCompletionContributor extends CompletionContributor {
  private static final ElementPattern<PsiElement> INSIDE_EXPRESSION = psiElement().withParent(GrExpression.class);
  private static final TObjectHashingStrategy<TypeConstraint> EXPECTED_TYPE_INFO_STRATEGY =
    new TObjectHashingStrategy<TypeConstraint>() {
      public int computeHashCode(final TypeConstraint object) {
        return object.getType().hashCode();
      }

      public boolean equals(final TypeConstraint o1, final TypeConstraint o2) {
        return o1.getClass().equals(o2.getClass()) && o1.getType().equals(o2.getType());
      }
    };

  public GroovySmartCompletionContributor() {
    extend(CompletionType.SMART, INSIDE_EXPRESSION, new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull final CompletionParameters params,
                                    ProcessingContext context,
                                    @NotNull final CompletionResultSet result) {
        final PsiElement position = params.getPosition();
        if (position.getParent() instanceof GrLiteral) return;

        final THashSet<TypeConstraint> _infos = new THashSet<TypeConstraint>();
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            ContainerUtil.addAll(_infos, getExpectedTypes(params));
          }
        });
        final Set<TypeConstraint> infos = ApplicationManager.getApplication().runReadAction(new Computable<Set<TypeConstraint>>() {
          public Set<TypeConstraint> compute() {
            return new THashSet<TypeConstraint>(_infos, EXPECTED_TYPE_INFO_STRATEGY);
          }
        });


        final PsiElement reference = position.getParent();
        if (reference == null) return;
        if (reference instanceof GrReferenceElement) {
          ((GrReferenceElement)reference).processVariants(new Consumer<Object>() {
            public void consume(Object variant) {
              PsiType type = null;

              final Object o;
              if (variant instanceof LookupElement) {
                o = ((LookupElement)variant).getObject();
              }
              else {
                o = variant;
              }
              if (o instanceof PsiElement) {
                type = getTypeByElement((PsiElement)o, position);
              }
              else if (o instanceof String) {
                if ("true".equals(o) || "false".equals(o)) {
                  type = PsiType.BOOLEAN;
                }
              }
              if (type == null) return;
              for (TypeConstraint info : infos) {
                if (info.satisfied(type, position.getManager(), GlobalSearchScope.allScope(position.getProject()))) {
                  final LookupElement lookupElement = GroovyCompletionUtil.getLookupElement(o);
                  result.addElement(lookupElement);
                  break;
                }
              }

            }
          });
        }
      }
    });
  }

  @Nullable
  public static TypeConstraint[] getExpectedTypes(CompletionParameters params) {
    final PsiElement position = params.getPosition();
    final GrExpression expression = PsiTreeUtil.getParentOfType(position, GrExpression.class);
    if (expression != null) {
      return GroovyExpectedTypesProvider.calculateTypeConstraints(expression);
    }
    return null;
  }

  @Nullable
  public static PsiType getTypeByElement(PsiElement element, PsiElement context) {
    //if(!element.isValid()) return null;
    if (element instanceof PsiType) {
      return (PsiType)element;
    }
    if (element instanceof PsiClass) {
      return PsiType.getJavaLangClass(context.getManager(), GlobalSearchScope.allScope(context.getProject()));
    }
    if (element instanceof PsiMethod) {
      return PsiUtil.getSmartReturnType((PsiMethod)element);
    }
    if (element instanceof GrVariable) {
      return ((GrVariable)element).getTypeGroovy();
    }
/*    if(element instanceof PsiKeyword){
      return getKeywordItemType(context, element.getText());
    }*/
    if (element instanceof GrExpression) {
      return ((GrExpression)element).getType();
    }

    return null;
  }


}
