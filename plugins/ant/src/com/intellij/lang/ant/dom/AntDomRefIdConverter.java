// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.dom;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.AntSupport;
import com.intellij.openapi.util.Ref;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 */
public class AntDomRefIdConverter extends Converter<AntDomElement> implements CustomReferenceConverter<AntDomElement>{

  @Override
  public AntDomElement fromString(@Nullable @NonNls String s, ConvertContext context) {
    if (s != null) {
      final AntDomElement element = AntSupport.getInvocationAntDomElement(context);
      if (element != null) {
        return findElementById(element.getContextAntProject(), s, CustomAntElementsRegistry.ourIsBuildingClasspathForCustomTagLoading.get());
      }
    }
    return null;
  }

  @Override
  public String toString(@Nullable AntDomElement antDomElement, ConvertContext context) {
    return antDomElement != null? antDomElement.getId().getRawText() : null;
  }

  @Override
  @NotNull
  public PsiReference[] createReferences(final GenericDomValue<AntDomElement> genericDomValue, final PsiElement element, ConvertContext context) {
    final AntDomElement invocationElement = AntSupport.getInvocationAntDomElement(context);
    return new PsiReference[] {new AntDomReferenceBase(element, true) {
      @Override
      public PsiElement resolve() {
        final AntDomElement value = genericDomValue.getValue();
        if (value == null) {
          return null;
        }
        final DomTarget target = DomTarget.getTarget(value, value.getId());
        if (target == null) {
          return null;
        }
        return PomService.convertToPsi(element.getProject(), target);
      }
      @Override
      @NotNull
      public Object[] getVariants() {
        if (invocationElement == null) {
          return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
        }
        final Set<String> variants = new LinkedHashSet<>();
        invocationElement.getContextAntProject().accept(new AntDomRecursiveVisitor() {
          @Override
          public void visitAntDomElement(AntDomElement element) {
            final String variant = element.getId().getRawText();
            if (variant != null) {
              variants.add(variant);
            }
            super.visitAntDomElement(element);
          }
        });
        return variants.size() > 0 ? ArrayUtil.toObjectArray(variants) : ArrayUtilRt.EMPTY_OBJECT_ARRAY;
      }

      @Override
      public String getUnresolvedMessagePattern() {
        return AntBundle.message("cannot.resolve.refid", getCanonicalText());
      }
    }};
  }

  @Nullable
  private static AntDomElement findElementById(AntDomElement from, final String id, final boolean skipCustomTags) {
    if (id.equals(from.getId().getRawText())) {
      return from;
    }
    final Ref<AntDomElement> result = new Ref<>(null);
    from.accept(new AntDomRecursiveVisitor() {
      @Override
      public void visitAntDomCustomElement(AntDomCustomElement custom) {
        if (!skipCustomTags) {
          super.visitAntDomCustomElement(custom);
        }
      }

      @Override
      public void visitAntDomElement(AntDomElement element) {
        if (result.get() != null) {
          return;
        }
        if (id.equals(element.getId().getRawText())) {
          result.set(element);
          return;
        }
        super.visitAntDomElement(element);
      }
    });

    return result.get();
  }
}
