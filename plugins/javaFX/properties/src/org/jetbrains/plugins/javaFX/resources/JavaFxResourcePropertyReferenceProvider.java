// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.resources;

import com.intellij.lang.properties.references.PropertyReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author Pavel.Dolgov
 */
class JavaFxResourcePropertyReferenceProvider extends PsiReferenceProvider {
  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    if (element instanceof XmlAttributeValue attr) {
      final String value = attr.getValue();
      if (value.startsWith("%") && value.length() > 1) {
        return new PsiReference[]{new JavaFxResourcePropertyReference(value.substring(1), attr)};
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }

  static class JavaFxResourcePropertyReference extends PropertyReference {
    JavaFxResourcePropertyReference(@NotNull String key, @NotNull XmlAttributeValue element) {
      super(key, element, null, false, new TextRange(2, key.length() + 2)); // "%key" - shift by 2 because the quote also counts
    }
  }
}
