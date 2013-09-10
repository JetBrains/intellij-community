/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.lang.Language;
import com.intellij.lang.injection.ConcatenationAwareInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.TemporaryPlacesRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;

import java.util.List;

/**
 * Created by Max Medvedev on 9/9/13
 */
public class GrConcatenationAwareInjector implements ConcatenationAwareInjector {
  private final LanguageInjectionSupport mySupport;

  @SuppressWarnings("UnusedParameters")
  public GrConcatenationAwareInjector(Configuration configuration, Project project, TemporaryPlacesRegistry temporaryPlacesRegistry) {
    mySupport = InjectorUtils.findNotNullInjectionSupport(GroovyLanguageInjectionSupport.GROOVY_SUPPORT_ID);
  }

  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement... operands) {
    if (operands.length == 0) return;

    Language language = findLanguage(operands);
    if (language != null) {
      PsiFile file = operands[0].getContainingFile();

      List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> list = ContainerUtil.newArrayList();

      boolean unparsable = false;

      String prefix = "";
      //String suffix = "";
      for (int i = 0; i < operands.length; i++) {
        PsiElement operand = operands[i];
        final ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(operand);
        if (manipulator == null) {
          unparsable = true;
          prefix += getStringPresentation(operand);
          if (i == operands.length - 1) {
            Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> last = ContainerUtil.getLastItem(list);
            assert last != null;
            InjectedLanguage injected = last.second;
            list.set(list.size() - 1, Trinity.create(last.first, InjectedLanguage.create(injected.getID(), injected.getPrefix(), prefix, false), last.third));
          }
        }
        else {
          InjectedLanguage injectedLanguage = InjectedLanguage.create(language.getID(), prefix, "", false);
          TextRange range = manipulator.getRangeInElement(operand);
          PsiLanguageInjectionHost host = (PsiLanguageInjectionHost)operand;
          list.add(Trinity.create(host, injectedLanguage, range));
          prefix = "";
        }
      }

      InjectorUtils.registerInjection(language, list, file, registrar);
      InjectorUtils.registerSupport(mySupport, false/*todo*/, registrar);
      InjectorUtils.putInjectedFileUserData(registrar, InjectedLanguageUtil.FRANKENSTEIN_INJECTION, unparsable);
    }
  }

  private static String getStringPresentation(PsiElement operand) {
    if (operand instanceof GrStringInjection) {
      return operand.getText();
    }
    return "missingValue";
  }

  @Nullable
  private static Language findLanguage(PsiElement[] operands) {
    PsiElement parent = PsiTreeUtil.findCommonParent(operands);
    Trinity<String, String, String> params = GrConcatenationInjector.findLanguageParams(parent);
    if (params != null) {
      return Language.findLanguageByID(params.first);
    }
    return null;
  }

}
