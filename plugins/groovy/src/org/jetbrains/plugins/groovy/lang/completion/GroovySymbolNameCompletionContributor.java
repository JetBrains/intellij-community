// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.JavaSymbolNameCompletionContributor;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.Collection;
import java.util.List;

public class GroovySymbolNameCompletionContributor extends JavaSymbolNameCompletionContributor {
  @NotNull
  @Override
  public Collection<LookupElement> getLookupElements(@NotNull PsiFile file, int invocationCount, @NotNull String prefix) {
    Collection<LookupElement> elements = super.getLookupElements(file, invocationCount, prefix);
    if (file instanceof GroovyFile) {
      GrMethod[] methods = ((GroovyFile)file).getMethods();
      for (GrMethod method : methods) {
        elements.add(LookupElementBuilder.create(method.getName())
                       .withTailText(" in "+file.getName(), true).withIcon(method.getIcon(0)));
      }
    }
    return elements;
  }

  @Override
  protected void processClassBody(int invocationCount, List<LookupElement> result, PsiElement aClass, String infix, String memberPrefix) {
    if (aClass instanceof GrTypeDefinition) {
      GrTypeDefinitionBody body = ((GrTypeDefinition)aClass).getBody();
      if (body != null) {
        super.processClassBody(invocationCount, result, body, infix, memberPrefix);
      }
    }
  }
}
