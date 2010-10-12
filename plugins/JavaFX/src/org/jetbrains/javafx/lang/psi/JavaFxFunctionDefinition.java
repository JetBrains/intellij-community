package org.jetbrains.javafx.lang.psi;

import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.StubBasedPsiElement;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxFunctionStub;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   16:19:20
 */
public interface JavaFxFunctionDefinition
  extends JavaFxQualifiedNamedElement, PsiNamedElement, StubBasedPsiElement<JavaFxFunctionStub>, JavaFxFunction {
  JavaFxFunctionDefinition[] EMPTY_ARRAY = new JavaFxFunctionDefinition[0];
}
