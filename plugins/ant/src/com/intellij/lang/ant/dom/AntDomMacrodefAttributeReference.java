// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.ArrayUtil;
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

  @Override
  public String getUnresolvedMessagePattern() {
    return AntBundle.message("unknown.macro.attribute", getCanonicalText());
  }

  @Override
  public PsiElement resolve() {
    return ResolveCache.getInstance(getElement().getProject()).resolveWithCaching(this, MyResolver.INSTANCE, false, false);
  }

  @Override
  public Object @NotNull [] getVariants() {
    final AntDomMacroDef parentMacrodef = getParentMacrodef();
    if (parentMacrodef != null) {
      final List<Object> variants = new ArrayList<>();
      for (AntDomMacrodefAttribute attribute : parentMacrodef.getMacroAttributes()) {
        final String attribName = attribute.getName().getStringValue();
        if (attribName != null && !attribName.isEmpty()) {
          final LookupElementBuilder builder = LookupElementBuilder.create(attribName);
          final LookupElement element = AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE.applyPolicy(builder);
          variants.add(element);
        }
      }
      return ArrayUtil.toObjectArray(variants);
    }
    return EMPTY_ARRAY;
  }

  private @Nullable AntDomMacroDef getParentMacrodef() {
    final PsiElement element = getElement();
    final DomElement domElement = DomUtil.getDomElement(element);
    if (domElement == null) {
      return null;
    }
    return domElement.getParentOfType(AntDomMacroDef.class, false);
  }

  private static class MyResolver implements ResolveCache.Resolver {

    static final MyResolver INSTANCE = new MyResolver();

    @Override
    public PsiElement resolve(@NotNull PsiReference psiReference, boolean incompleteCode) {
      final PsiElement element = psiReference.getElement();
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
