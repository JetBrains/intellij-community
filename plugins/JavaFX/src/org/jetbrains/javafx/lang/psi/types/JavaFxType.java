package org.jetbrains.javafx.lang.psi.types;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public abstract class JavaFxType extends PsiType {
  protected JavaFxType() {
    super(PsiAnnotation.EMPTY_ARRAY);
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public boolean equalsToText(@NonNls String text) {
    return text.endsWith(getCanonicalText());
  }

  @Override
  public String getInternalCanonicalText() {
    return getCanonicalText();
  }

  @Override
  public <A> A accept(PsiTypeVisitor<A> visitor) {
    return visitor.visitType(this);
  }

  @NotNull
  @Override
  public PsiType[] getSuperTypes() {
    return EMPTY_ARRAY;
  }
}
