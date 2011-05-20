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
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.InitialPatternCondition;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentProvider;
import org.jetbrains.plugins.groovy.lang.completion.handlers.NamedArgumentInsertHandler;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyElementPattern;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Map;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyPatterns.namedArgument;

/**
 * @author peter
 */
class MapArgumentCompletionProvider extends CompletionProvider<CompletionParameters> {

  private MapArgumentCompletionProvider() {
  }

  public static void register(CompletionContributor contributor) {
    MapArgumentCompletionProvider instance = new MapArgumentCompletionProvider();

    ElementPattern<PsiElement> inArgumentListOfCall = psiElement().withParent(psiElement(GrReferenceExpression.class).withParent(
      StandardPatterns.or(
        psiElement(GrArgumentList.class).withParent(GrCall.class),
        new GroovyElementPattern.Capture<GrListOrMap>(new InitialPatternCondition<GrListOrMap>(GrListOrMap.class) {
          @Override
          public boolean accepts(@Nullable Object o, ProcessingContext context) {
            if (!(o instanceof GrListOrMap)) return false;
            PsiElement parent = ((GrListOrMap)o).getParent();
            if (!(parent instanceof GrArgumentList)) return false;

            GrArgumentList argumentList = (GrArgumentList)parent;
            if (argumentList.getNamedArguments().length > 0) return false;
            if (argumentList.getExpressionArgumentIndex((GrListOrMap)o) > 0) return false;

            if (!(argumentList.getParent() instanceof GrCall)) return false;

            return true;
          }
        })
      )
    ));

    ElementPattern<PsiElement> inLabel = psiElement(GroovyTokenTypes.mIDENT).withParent(psiElement(GrArgumentLabel.class).withParent(
      namedArgument().isParameterOfMethodCall(null)));

    contributor.extend(CompletionType.BASIC, inArgumentListOfCall, instance);
    contributor.extend(CompletionType.BASIC, inLabel, instance);

    contributor.extend(CompletionType.SMART, inArgumentListOfCall, instance);
    contributor.extend(CompletionType.SMART, inLabel, instance);
  }

  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters,
                                ProcessingContext context,
                                @NotNull CompletionResultSet result) {
    PsiElement mapOrArgumentList;

    PsiElement parent = parameters.getPosition().getParent();
    if (parent instanceof GrReferenceExpression) {
      if (((GrReferenceExpression)parent).getQualifier() != null) return;
      mapOrArgumentList = parent.getParent();
    }
    else {
      mapOrArgumentList = parent.getParent().getParent();
    }

    PsiElement argumentList = mapOrArgumentList instanceof GrArgumentList ? mapOrArgumentList : mapOrArgumentList.getParent();

    final GrCall call = (GrCall)argumentList.getParent();

    Map<String, GroovyNamedArgumentProvider.ArgumentDescriptor> map = GroovyNamedArgumentProvider.getNamedArgumentsFromAllProviders(call, null, true);

    for (GrNamedArgument argument : PsiUtil.getFirstMapNamedArguments(call)) {
      map.remove(argument.getLabelName());
    }

    for (Map.Entry<String, GroovyNamedArgumentProvider.ArgumentDescriptor> entry : map.entrySet()) {
      LookupElement lookup = LookupElementBuilder.create(entry.getKey())
        .setIcon(GroovyIcons.DYNAMIC)
        .setInsertHandler(NamedArgumentInsertHandler.INSTANCE);

      if (entry.getValue().isShowFirst()) {
        lookup = PrioritizedLookupElement.withPriority(lookup, 1);
      }

      result.addElement(lookup);
    }
  }
}
