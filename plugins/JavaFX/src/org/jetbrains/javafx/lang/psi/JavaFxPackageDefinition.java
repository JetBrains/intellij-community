package org.jetbrains.javafx.lang.psi;

import com.intellij.psi.StubBasedPsiElement;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxPackageDefinitionStub;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   16:19:20
 */
public interface JavaFxPackageDefinition extends JavaFxElement, JavaFxQualifiedNamedElement, StubBasedPsiElement<JavaFxPackageDefinitionStub> {
}
