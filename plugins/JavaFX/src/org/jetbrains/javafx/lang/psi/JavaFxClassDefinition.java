package org.jetbrains.javafx.lang.psi;

import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.StubBasedPsiElement;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxClassStub;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   16:19:20
 */
public interface JavaFxClassDefinition extends JavaFxQualifiedNamedElement, PsiNamedElement, StubBasedPsiElement<JavaFxClassStub> {
  JavaFxElement[] getMembers();

  JavaFxReferenceElement[] getSuperClassElements();
}
