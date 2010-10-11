package org.jetbrains.javafx.lang.psi.impl.types;

import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.psi.JavaFxFunction;
import org.jetbrains.javafx.lang.psi.JavaFxSignature;
import org.jetbrains.javafx.lang.psi.types.JavaFxFunctionType;
import org.jetbrains.javafx.lang.psi.types.JavaFxType;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxFunctionTypeImpl extends JavaFxType implements JavaFxFunctionType {
  private final JavaFxSignature mySignature;
  private final PsiType myReturnType;

  public JavaFxFunctionTypeImpl(@NotNull final JavaFxSignature signature) {
    mySignature = signature;
    myReturnType = signature.getReturnType();
  }

  public JavaFxFunctionTypeImpl(@NotNull final JavaFxFunction function) {
    mySignature = function.getSignature();
    myReturnType = function.getReturnType();
  }

  @Override
  @Nullable
  public PsiType getReturnType() {
    return myReturnType;
  }

  // TODO:
  @Override
  public String getPresentableText() {
    return "function " + mySignature.getText() + ": " + myReturnType.getPresentableText();
  }

  @Override
  public String getCanonicalText() {
    return getPresentableText();
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return mySignature.getResolveScope();
  }
}
