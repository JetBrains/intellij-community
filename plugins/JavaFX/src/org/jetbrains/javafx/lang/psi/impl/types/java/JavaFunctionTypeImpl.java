package org.jetbrains.javafx.lang.psi.impl.types.java;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.javafx.lang.psi.types.JavaFxFunctionType;
import org.jetbrains.javafx.lang.psi.types.JavaFxType;

/**
 * Created by IntelliJ IDEA.
 * @author: Alexey.Ivanov
 */
public class JavaFunctionTypeImpl extends JavaFxType implements JavaFxFunctionType {
  private final PsiMethod myMethod;

  public JavaFunctionTypeImpl(PsiMethod method) {
    myMethod = method;
  }

  @Override
  public PsiType getReturnType() {
    return myMethod.getReturnType();
  }

  // TODO:
  @Override
  public String getPresentableText() {
    return "function " + myMethod.getName();
  }

  @Override
  public String getCanonicalText() {
    return getPresentableText();
  }
  @Override
  public GlobalSearchScope getResolveScope() {
    return myMethod.getResolveScope();
  }
}
