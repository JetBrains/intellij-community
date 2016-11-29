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
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentProvider;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentUtilKt;
import org.jetbrains.plugins.groovy.highlighter.GroovySyntaxHighlighter;
import org.jetbrains.plugins.groovy.lang.completion.handlers.NamedArgumentInsertHandler;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author peter
 */
class MapArgumentCompletionProvider extends CompletionProvider<CompletionParameters> {

  // @formatter:off

  // [<caret>]
  // [<some values, initializers or named arguments>, <caret>]
  // foo <caret>
  // foo (<caret>)
  public static final ElementPattern<PsiElement> IN_ARGUMENT_LIST_OF_CALL = PlatformPatterns
    .psiElement().withParent(PlatformPatterns.psiElement(GrReferenceExpression.class).withParent(
    StandardPatterns.or(PlatformPatterns.psiElement(GrArgumentList.class), PlatformPatterns.psiElement(GrListOrMap.class)))
  );

  // [<caret> : ]
  // [<some values>, <caret> : ]
  public static final ElementPattern<PsiElement> IN_LABEL = PlatformPatterns.psiElement(GroovyTokenTypes.mIDENT).withParent(GrArgumentLabel.class);

  // @formatter:on

  private MapArgumentCompletionProvider() {
  }

  public static void register(CompletionContributor contributor) {
    MapArgumentCompletionProvider instance = new MapArgumentCompletionProvider();

    contributor.extend(CompletionType.BASIC, IN_ARGUMENT_LIST_OF_CALL, instance);
    contributor.extend(CompletionType.BASIC, IN_LABEL, instance);
  }

  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters,
                                ProcessingContext context,
                                @NotNull CompletionResultSet result) {
    PsiElement mapOrArgumentList = findMapOrArgumentList(parameters);
    if (mapOrArgumentList == null) {
      return;
    }

    if (isMapKeyCompletion(parameters)) {
      result.stopHere();
    }

    final Map<String, NamedArgumentDescriptor> map = ContainerUtil.newHashMap(calculateNamedArguments(mapOrArgumentList));

    for (GrNamedArgument argument : getSiblingNamedArguments(mapOrArgumentList)) {
      map.remove(argument.getLabelName());
    }

    for (Map.Entry<String, NamedArgumentDescriptor> entry : map.entrySet()) {
      LookupElementBuilder lookup = LookupElementBuilder.create(entry.getValue(), entry.getKey())
        .withInsertHandler(NamedArgumentInsertHandler.INSTANCE)
        .withTailText(":");

      if (entry.getValue().getPriority() == NamedArgumentDescriptor.Priority.UNLIKELY) {
        lookup = lookup.withItemTextForeground(GroovySyntaxHighlighter.MAP_KEY.getDefaultAttributes().getForegroundColor());
      }
      else {
        lookup = lookup.withIcon(JetgroovyIcons.Groovy.DynamicProperty);
      }

      LookupElement customized = entry.getValue().customizeLookupElement(lookup);
      result.addElement(customized == null ? lookup : customized);
    }
  }

  public static boolean isMapKeyCompletion(CompletionParameters parameters) {
    PsiElement mapOrArgumentList = findMapOrArgumentList(parameters);
    return mapOrArgumentList instanceof GrListOrMap && ((GrListOrMap)mapOrArgumentList).getNamedArguments().length > 0;
  }

  @Nullable
  private static PsiElement findMapOrArgumentList(CompletionParameters parameters) {
    PsiElement parent = parameters.getPosition().getParent();
    if (parent instanceof GrReferenceExpression) {
      if (((GrReferenceExpression)parent).getQualifier() != null) return null;
      return parent.getParent();
    }
    if (parent == null || parent.getParent() == null) {
      return null;
    }
    return parent.getParent().getParent();
  }

  @NotNull
  private static Map<String, NamedArgumentDescriptor> findOtherNamedArgumentsInFile(PsiElement mapOrArgumentList) {
    final Map<String, NamedArgumentDescriptor> map = new HashMap<>();
    mapOrArgumentList.getContainingFile().accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (element instanceof GrArgumentLabel) {
          final String name = ((GrArgumentLabel)element).getName();
          if (GroovyNamesUtil.isIdentifier(name)) {
            map.put(name, NamedArgumentDescriptor.SIMPLE_UNLIKELY);
          }
        }
        super.visitElement(element);
      }
    });
    return map;
  }

  private static GrNamedArgument[] getSiblingNamedArguments(PsiElement mapOrArgumentList) {
    if (mapOrArgumentList instanceof GrListOrMap) {
      return ((GrListOrMap)mapOrArgumentList).getNamedArguments();
    }

    PsiElement argumentList = mapOrArgumentList instanceof GrArgumentList ? mapOrArgumentList : mapOrArgumentList.getParent();
    if (argumentList instanceof GrArgumentList) {
      if (argumentList.getParent() instanceof GrCall) {
        return PsiUtil.getFirstMapNamedArguments((GrCall)argumentList.getParent());
      }
    }

    return GrNamedArgument.EMPTY_ARRAY;
  }

  @NotNull
  private static Map<String, NamedArgumentDescriptor> calculateNamedArguments(@NotNull PsiElement mapOrArgumentList) {
    Map<String, NamedArgumentDescriptor> map = calcNamedArgumentsForCall(mapOrArgumentList);

    if ((map == null || map.isEmpty()) && mapOrArgumentList instanceof GrListOrMap) {
      map = NamedArgumentUtilKt.getDescriptors((GrListOrMap)mapOrArgumentList);
    }

    if (map == null || map.isEmpty()) {
      map = findOtherNamedArgumentsInFile(mapOrArgumentList);
    }

    return map;
  }

  @Nullable
  private static Map<String, NamedArgumentDescriptor> calcNamedArgumentsForCall(@NotNull PsiElement mapOrArgumentList) {
    PsiElement argumentList = mapOrArgumentList instanceof GrArgumentList ? mapOrArgumentList : mapOrArgumentList.getParent();
    if (argumentList instanceof GrArgumentList) {
      if (mapOrArgumentList instanceof GrListOrMap) {
        if (((GrArgumentList)argumentList).getNamedArguments().length > 0 ||
            ((GrArgumentList)argumentList).getExpressionArgumentIndex((GrListOrMap)mapOrArgumentList) > 0) {
          return Collections.emptyMap();
        }
      }

      if (argumentList.getParent() instanceof GrCall) {
        return GroovyNamedArgumentProvider.getNamedArgumentsFromAllProviders((GrCall)argumentList.getParent(), null, true);
      }
    }

    return Collections.emptyMap();
  }
}
