package org.jetbrains.plugins.javaFX.refactoring;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.beanProperties.BeanPropertyElement;
import com.intellij.psi.util.PropertyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.refs.JavaFxPropertyReference;

/**
 * @author Pavel.Dolgov
 */
class JavaFxPropertyElement extends BeanPropertyElement {
  private final JavaFxPropertyReference myPropertyReference;

  private JavaFxPropertyElement(JavaFxPropertyReference propertyReference, String propertyName, PsiMethod method) {
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
  static PsiElement fromReference(@NotNull final JavaFxPropertyReference propertyReference) {
    final PsiElement element = propertyReference.resolve();
    if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      final String propertyName = PropertyUtil.getPropertyName(method);
      if (propertyName != null) {
        return new JavaFxPropertyElement(propertyReference, propertyName, method);
      }
    }
    if (element instanceof PsiField) {
      return element;
    }
    return null;
  }
}
