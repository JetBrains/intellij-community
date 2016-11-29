package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

/**
 * @author Pavel.Dolgov
 */
public abstract class JavaFxPropertyReference<T extends PsiElement> extends PsiReferenceBase<T> {
  protected final PsiClass myPsiClass;

  public JavaFxPropertyReference(@NotNull T element, PsiClass aClass) {
    super(element);
    myPsiClass = aClass;
  }

  @Nullable
  public PsiMethod getGetter() {
    if (myPsiClass == null) return null;
    return JavaFxPsiUtil.findPropertyGetter(myPsiClass, getPropertyName());
  }

  @Nullable
  public PsiMethod getSetter() {
    if (myPsiClass == null) return null;
    return JavaFxPsiUtil.findInstancePropertySetter(myPsiClass, getPropertyName());
  }

  @Nullable
  public PsiField getField() {
    if (myPsiClass == null) return null;
    return myPsiClass.findFieldByName(getPropertyName(), true);
  }

  @Nullable
  public PsiMethod getObservableGetter() {
    if (myPsiClass == null) return null;
    return JavaFxPsiUtil.findObservablePropertyGetter(myPsiClass, getPropertyName());
  }

  @Nullable
  public PsiMethod getStaticSetter() {
    return null;
  }

  @Nullable
  public PsiType getType() {
    return JavaFxPsiUtil.getReadablePropertyType(resolve());
  }

  @Nullable
  public abstract String getPropertyName();
}
