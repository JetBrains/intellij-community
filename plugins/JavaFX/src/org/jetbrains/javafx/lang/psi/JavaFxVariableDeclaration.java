package org.jetbrains.javafx.lang.psi;

import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.StubBasedPsiElement;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxVariableStub;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   16:19:20
 */
public interface JavaFxVariableDeclaration
  extends JavaFxValueExpression, JavaFxQualifiedNamedElement, PsiNamedElement, StubBasedPsiElement<JavaFxVariableStub> {
  JavaFxVariableDeclaration[] EMPTY_ARRAY = new JavaFxVariableDeclaration[0];

  @Nullable
  JavaFxExpression getInitializer();

  @Nullable
  JavaFxTypeElement getTypeElement();
}
