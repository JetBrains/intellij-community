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
package org.jetbrains.android.dom.converters;

import com.android.resources.ResourceType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.android.dom.resources.ResourceNameConverter;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class ParentStyleConverter extends ResourceReferenceConverter {
  public ParentStyleConverter() {
    super(ResourceType.STYLE.getName(), false, false);
    setAllowAttributeReferences(false);
  }

  @NotNull
  @Override
  public PsiReference[] createReferences(GenericDomValue<ResourceValue> value, PsiElement element, ConvertContext context) {
    final PsiReference[] refsFromSuper = super.createReferences(value, element, context);

    final ResourceValue resValue = value.getValue();

    if (resValue == null || resValue.getPackage() != null) {
      return refsFromSuper;
    }
    final AndroidFacet facet = AndroidFacet.getInstance(context);

    if (facet != null) {
      final PsiReference[] refs = getReferencesInStyleName(value, facet);

      if (refs.length > 0) {
        return ArrayUtil.mergeArrays(refsFromSuper, refs);
      }
    }
    return refsFromSuper;
  }

  @NotNull
  private static PsiReference[] getReferencesInStyleName(@NotNull GenericDomValue<?> value, @NotNull AndroidFacet facet) {
    String s = value.getStringValue();

    if (s == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    int start = 0;
    final int idx = s.indexOf('/');

    if (idx >= 0) {
      start = idx + 1;
      s = s.substring(start);
    }
    final String[] ids = s.split("\\.");
    if (ids.length < 2) {
      return PsiReference.EMPTY_ARRAY;
    }
    final List<PsiReference> result = new ArrayList<PsiReference>(ids.length - 1);
    int offset = s.length();

    for (int i = ids.length - 1; i >= 0; i--) {
      final String styleName = s.substring(0, offset);

      if (i < ids.length - 1) {
        final ResourceValue val = ResourceValue.referenceTo((char)0, null, ResourceType.STYLE.getName(), styleName);
        result.add(new ResourceNameConverter.MyParentStyleReference(value, new TextRange(1 + start, 1 + start + offset), val, facet));
      }
      if (ResourceNameConverter.hasExplicitParent(facet, styleName)) {
        break;
      }
      offset = offset - ids[i].length() - 1;
    }
    return result.toArray(new PsiReference[result.size()]);
  }
}
