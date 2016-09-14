package org.jetbrains.plugins.javaFX.resources;

import com.intellij.lang.properties.references.PropertyReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Pavel.Dolgov
 */
class JavaFxResourcePropertyReferenceProvider extends PsiReferenceProvider {
  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    if (element instanceof XmlAttributeValue) {
      final String value = ((XmlAttributeValue)element).getValue();
      if (value != null && value.startsWith("%") && value.length() > 1) {
        return new PsiReference[]{new JavaFxResourcePropertyReference(value.substring(1), (XmlAttributeValue)element)};
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }

  static class JavaFxResourcePropertyReference extends PropertyReference {
    public JavaFxResourcePropertyReference(@NotNull String key, @NotNull XmlAttributeValue element) {
      super(key, element, null, false, new TextRange(2, key.length() + 2)); // "%key" - shift by 2 because the quote also counts
    }
  }
}
