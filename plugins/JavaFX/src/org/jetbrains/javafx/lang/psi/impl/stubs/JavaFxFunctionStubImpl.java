package org.jetbrains.javafx.lang.psi.impl.stubs;

import com.intellij.psi.stubs.StubElement;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxFunctionDefinition;
import org.jetbrains.javafx.lang.psi.impl.JavaFxQualifiedName;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxFunctionStub;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxFunctionStubImpl extends JavaFxQualifiedNamedStubImpl<JavaFxFunctionDefinition> implements JavaFxFunctionStub {
  private final String myName;

  public JavaFxFunctionStubImpl(final StubElement parent, final JavaFxQualifiedName qualifiedName, final String name) {
    super(parent, JavaFxElementTypes.FUNCTION_DEFINITION, qualifiedName);
    myName = name;
  }

  @Override
  public String getName() {
    return myName;
  }
}
