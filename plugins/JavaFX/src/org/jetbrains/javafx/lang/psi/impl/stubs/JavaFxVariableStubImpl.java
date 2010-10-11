package org.jetbrains.javafx.lang.psi.impl.stubs;

import com.intellij.psi.stubs.StubElement;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxVariableDeclaration;
import org.jetbrains.javafx.lang.psi.impl.JavaFxQualifiedName;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxVariableStub;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxVariableStubImpl extends JavaFxQualifiedNamedStubImpl<JavaFxVariableDeclaration> implements JavaFxVariableStub {
  private final String myName;

  public JavaFxVariableStubImpl(final StubElement parent, final JavaFxQualifiedName qualifiedName, final String name) {
    super(parent, JavaFxElementTypes.VARIABLE_DECLARATION, qualifiedName);
    myName = name;
  }

  @Override
  public String getName() {
    return myName;
  }
}
