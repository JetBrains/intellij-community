// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.util.ProcessingContext;
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

import java.awt.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

final class MapArgumentCompletionProvider extends CompletionProvider<CompletionParameters> {

  // @formatter:off

  // [<caret>]
  // [<some values, initializers or named arguments>, <caret>]
  // foo <caret>
  // foo (<caret>)
  // foo (aa<caret> bb)
  public static final ElementPattern<PsiElement> IN_ARGUMENT_LIST_OF_CALL = PlatformPatterns
    .psiElement().withParent(PlatformPatterns.psiElement(GrReferenceExpression.class).withParent(
    StandardPatterns.or(PlatformPatterns.psiElement(GrArgumentList.class), PlatformPatterns.psiElement().withParent(GrArgumentList.class), PlatformPatterns.psiElement(GrListOrMap.class)))
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
                                @NotNull ProcessingContext context,
                                @NotNull CompletionResultSet result) {
    PsiElement mapOrArgumentList = findMapOrArgumentList(parameters);
    if (mapOrArgumentList == null) {
      return;
    }

    if (isMapKeyCompletion(parameters)) {
      result.stopHere();
    }

    final Map<String, NamedArgumentDescriptor> map = new HashMap<>(calculateNamedArguments(mapOrArgumentList));

    for (GrNamedArgument argument : getSiblingNamedArguments(mapOrArgumentList)) {
      map.remove(argument.getLabelName());
    }

    for (Map.Entry<String, NamedArgumentDescriptor> entry : map.entrySet()) {
      LookupElementBuilder lookup = LookupElementBuilder.create(entry.getValue(), entry.getKey())
        .withInsertHandler(NamedArgumentInsertHandler.INSTANCE)
        .withTailText(":");

      if (entry.getValue().getPriority() == NamedArgumentDescriptor.Priority.UNLIKELY) {
        TextAttributes defaultAttributes = GroovySyntaxHighlighter.MAP_KEY.getDefaultAttributes();
        if (defaultAttributes != null) {
          Color fg = defaultAttributes.getForegroundColor();
          if (fg != null) {
            lookup = lookup.withItemTextForeground(fg);
          }
        }
      }
      else {
        lookup = lookup.withIcon(IconManager.getInstance().getPlatformIcon(PlatformIcons.Property));
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
      public void visitElement(@NotNull PsiElement element) {
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
