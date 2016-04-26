package org.jetbrains.plugins.javaFX.refactoring;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.beanProperties.BeanPropertyElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.refs.JavaFxStaticPropertyReference;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxStaticPropertyElement extends BeanPropertyElement {
  private final JavaFxStaticPropertyReference myPropertyReference;

  private JavaFxStaticPropertyElement(JavaFxStaticPropertyReference propertyReference, String propertyName, PsiMethod method) {
    super(method, propertyName);
    myPropertyReference = propertyReference;
  }

  @Nullable
  @Override
  public PsiType getPropertyType() {
    return myPropertyReference.getType();
  }

  @Override
  public String getTypeName() {
    return "property";
  }

  @Nullable
  static PsiElement fromReference(@NotNull final JavaFxStaticPropertyReference propertyReference) {
    final PsiMethod method = propertyReference.getStaticMethod();
    if (method != null) {
      final String propertyName = propertyReference.getPropertyName();
      if (propertyName != null) {
        return new JavaFxStaticPropertyElement(propertyReference, propertyName, method);
      }
    }
    return null;
  }
}
