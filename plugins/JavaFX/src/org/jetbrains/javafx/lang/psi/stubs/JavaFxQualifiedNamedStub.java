package org.jetbrains.javafx.lang.psi.stubs;

import com.intellij.psi.stubs.StubElement;
import org.jetbrains.javafx.lang.psi.JavaFxQualifiedNamedElement;
import org.jetbrains.javafx.lang.psi.impl.JavaFxQualifiedName;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public interface JavaFxQualifiedNamedStub<T extends JavaFxQualifiedNamedElement> extends StubElement<T> {
  JavaFxQualifiedName getQualifiedName();

  String getName();
}
