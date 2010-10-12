package org.jetbrains.javafx.lang.psi;

import com.intellij.psi.PsiType;
import com.intellij.psi.StubBasedPsiElement;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxSignatureStub;

/**
 * Created by IntelliJ IDEA.
 * @author : Alexey.Ivanov
 */
public interface JavaFxSignature extends JavaFxElement, StubBasedPsiElement<JavaFxSignatureStub> {
  JavaFxSignature[] EMPTY_ARRAY = new JavaFxSignature[0];

  @Nullable
  JavaFxParameterList getParameterList();

  @Nullable
  PsiType getReturnType();
}
