package org.jetbrains.javafx.lang.psi;

import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.StubBasedPsiElement;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxParameterStub;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public interface JavaFxParameter extends JavaFxElement, PsiNamedElement, StubBasedPsiElement<JavaFxParameterStub> {
  JavaFxParameter[] EMPTY_ARRAY = new JavaFxParameter[0];

  @Nullable
  JavaFxTypeElement getTypeElement();

  @Nullable
  PsiType getType();
}
