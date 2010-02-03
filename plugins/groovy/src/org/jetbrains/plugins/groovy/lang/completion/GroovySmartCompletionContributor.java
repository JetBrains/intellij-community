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

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItemUtil;
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
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInsight.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.Arrays;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author Maxim.Medvedev
 */
public class GroovySmartCompletionContributor extends CompletionContributor {
  private static final ElementPattern<PsiElement> INSIDE_EXPRESSION = psiElement().withParent(GrExpression.class);
  private static final TObjectHashingStrategy<ExpectedTypeInfo> EXPECTED_TYPE_INFO_STRATEGY =
    new TObjectHashingStrategy<ExpectedTypeInfo>() {
      public int computeHashCode(final ExpectedTypeInfo object) {
        return object.getType().hashCode();
      }

      public boolean equals(final ExpectedTypeInfo o1, final ExpectedTypeInfo o2) {
        return o1.getType().equals(o2.getType());
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

        final THashSet<ExpectedTypeInfo> _infos = new THashSet<ExpectedTypeInfo>();
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            _infos.addAll(Arrays.asList(getExpectedTypes(params)));
          }
        });
        final Set<ExpectedTypeInfo> infos = ApplicationManager.getApplication().runReadAction(new Computable<Set<ExpectedTypeInfo>>() {
          public Set<ExpectedTypeInfo> compute() {
            return new THashSet<ExpectedTypeInfo>(_infos, EXPECTED_TYPE_INFO_STRATEGY);
          }
        });


        final PsiElement reference = position.getParent();
        if (reference == null) return;
        if (reference instanceof GrReferenceElement) {
          ((GrReferenceElement)reference).processVariants(new Consumer<Object>() {
            public void consume(Object variant) {
              PsiType type = null;
              if (variant instanceof PsiElement) {
                type = getTypeByElement((PsiElement)variant, position);
              }
              else if (variant instanceof String) {
                if ("true".equals(variant) || "false".equals(variant)) {
                  type = PsiType.BOOLEAN;
                }
              }
              if (type == null) return;
              for (ExpectedTypeInfo info : infos) {
                if (TypesUtil.isAssignableByMethodCallConversion(info.getType(), type, position.getManager(), GlobalSearchScope.allScope(position.getProject()))) {
                  final LookupElement lookupElement = LookupItemUtil.objectToLookupItem(variant);
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
  public static ExpectedTypeInfo[] getExpectedTypes(CompletionParameters params) {
    final PsiElement position = params.getPosition();
    final GrExpression expression = PsiTreeUtil.getParentOfType(position, GrExpression.class);
    if (expression != null) {
      return GroovyExpectedTypesProvider.getInstance(position.getProject())
        .getExpectedTypes(expression, true, params.getCompletionType() == CompletionType.SMART);
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
      return ((PsiMethod)element).getReturnType();
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
