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
package org.intellij.plugins.intelliLang.inject.groovy;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.patterns.compiler.PatternCompilerImpl;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author Gregory.Shrago
 */
public class PatternEditorContextMembersProvider implements NonCodeMembersProcessor {

  public static final Key<AtomicNotNullLazyValue<PsiFile>> INJECTION_PARSED_CONTEXT = Key.create("INJECTION_PARSED_CONTEXT");

  public boolean processNonCodeMembers(PsiType type, PsiScopeProcessor processor, PsiElement place, boolean forCompletion) {
    final PsiFile file = place.getContainingFile().getOriginalFile();
    final BaseInjection injection = file.getUserData(BaseInjection.INJECTION_KEY);
    if (injection == null) return true;
    final PsiFile contextFile =
      ((UserDataHolderEx)file).putUserDataIfAbsent(INJECTION_PARSED_CONTEXT, new AtomicNotNullLazyValue<PsiFile>() {
        @NotNull
        @Override
        protected PsiFile compute() {
          return parseInjectionContext(injection, file.getProject());
        }
      }).getValue();
    for (PsiElement cur = contextFile.getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (cur instanceof PsiNamedElement && !ResolveUtil.processElement(processor, (PsiNamedElement)cur)) return false;
    }
    return true;
  }

  private static PsiFile parseInjectionContext(@NotNull BaseInjection injection, Project project) {
    final String text = new PatternCompilerImpl(InjectorUtils.findInjectionSupport(injection.getSupportId()).getPatternClasses()).dumpContextDeclarations();
    return PsiFileFactory.getInstance(project).createFileFromText("context.groovy", GroovyFileType.GROOVY_FILE_TYPE, text);
  }
}
