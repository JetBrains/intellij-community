/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.dgm;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;

import java.util.Map;

/**
 * @author Max Medvedev
 */
public class DGMCompletionContributor extends CompletionContributor {
  public DGMCompletionContributor() {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(PropertiesTokenTypes.KEY_CHARACTERS),
           new CompletionProvider<CompletionParameters>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           ProcessingContext context,
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
           new CompletionProvider<CompletionParameters>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           ProcessingContext context,
                                           @NotNull final CompletionResultSet result) {
               PsiElement position = parameters.getPosition();
               if (!DGMUtil.isInDGMFile(position)) return;

               AllClassesGetter.processJavaClasses(parameters, result.getPrefixMatcher(), true, new Consumer<PsiClass>() {
                 @Override
                 public void consume(PsiClass aClass) {
                   result.addElement(GroovyCompletionUtil.createClassLookupItem(aClass));
                 }
               });
             }
           });
  }
}
