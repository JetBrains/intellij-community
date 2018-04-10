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
package com.intellij.lang.ant.dom;

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ant.AntBundle;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class AntDomMacrodefAttributeReference extends AntDomReferenceBase{

  public AntDomMacrodefAttributeReference(PsiElement element, TextRange range) {
    super(element, range, true);
  }

  public String getUnresolvedMessagePattern() {
    return AntBundle.message("unknown.macro.attribute", getCanonicalText());
  }

  public PsiElement resolve() {
    return ResolveCache.getInstance(getElement().getProject()).resolveWithCaching(this, MyResolver.INSTANCE, false, false);
  }

  @NotNull 
  public Object[] getVariants() {
    final AntDomMacroDef parentMacrodef = getParentMacrodef();
    if (parentMacrodef != null) {
      final List variants = new ArrayList();
      for (AntDomMacrodefAttribute attribute : parentMacrodef.getMacroAttributes()) {
        final String attribName = attribute.getName().getStringValue();
        if (attribName != null && attribName.length() > 0) {
          final LookupElementBuilder builder = LookupElementBuilder.create(attribName);
          final LookupElement element = AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE.applyPolicy(builder);
          variants.add(element);
        }
      }
      return ContainerUtil.toArray(variants, new Object[variants.size()]);
    }
    return EMPTY_ARRAY;
  }

  @Nullable 
  private AntDomMacroDef getParentMacrodef() {
    final PsiElement element = getElement();
    if (element == null) {
      return null;
    }
    final DomElement domElement = DomUtil.getDomElement(element);
    if (domElement == null) {
      return null;
    }
    return domElement.getParentOfType(AntDomMacroDef.class, false);
  }

  private static class MyResolver implements ResolveCache.Resolver {
    
    static final MyResolver INSTANCE = new MyResolver();
    
    public PsiElement resolve(@NotNull PsiReference psiReference, boolean incompleteCode) {
      final PsiElement element = psiReference.getElement();
      if (element == null) {
        return null;
      }
      final DomElement domElement = DomUtil.getDomElement(element);
      if (domElement == null) {
        return null;
      }
      final AntDomMacroDef macrodef = domElement.getParentOfType(AntDomMacroDef.class, false);
      if (macrodef == null) {
        return null;
      }
      final String name = AntStringResolver.computeString(domElement, psiReference.getCanonicalText());
      for (AntDomMacrodefAttribute attribute : macrodef.getMacroAttributes()) {
        if (name.equals(attribute.getName().getStringValue())) {
          final DomTarget target = DomTarget.getTarget(attribute);
          return target != null? PomService.convertToPsi(target) : null;
        }
      }
      return null;
    }
  }
}
