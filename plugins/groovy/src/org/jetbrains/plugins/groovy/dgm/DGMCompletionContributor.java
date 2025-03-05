// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dgm;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;

import java.util.Map;

/**
 * @author Max Medvedev
 */
public final class DGMCompletionContributor extends CompletionContributor {
  public DGMCompletionContributor() {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(PropertiesTokenTypes.KEY_CHARACTERS),
           new CompletionProvider<>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           @NotNull ProcessingContext context,
                                           @NotNull CompletionResultSet result) {
               PsiElement position = parameters.getPosition();
               if (!DGMUtil.isInDGMFile(position)) return;

               Map<String, String> map = ((PropertiesFile)position.getContainingFile()).getNamesMap();
               for (String key : DGMUtil.KEYS) {
                 if (!map.containsKey(key)) {
                   result.addElement(LookupElementBuilder.create(key));
                 }
               }
             }
           });

    extend(CompletionType.BASIC, PlatformPatterns.psiElement(PropertiesTokenTypes.VALUE_CHARACTERS),
           new CompletionProvider<>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           @NotNull ProcessingContext context,
                                           final @NotNull CompletionResultSet result) {
               PsiElement position = parameters.getPosition();
               if (!DGMUtil.isInDGMFile(position)) return;

               AllClassesGetter.processJavaClasses(parameters, result.getPrefixMatcher(), true,
                                                   aClass -> result.addElement(GroovyCompletionUtil.createClassLookupItem(aClass)));
             }
           });
  }
}
