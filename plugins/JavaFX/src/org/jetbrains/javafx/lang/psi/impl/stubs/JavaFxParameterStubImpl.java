package org.jetbrains.javafx.lang.psi.impl.stubs;

import com.intellij.psi.stubs.StubElement;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxParameter;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxParameterStub;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxParameterStubImpl extends JavaFxNamedStub<JavaFxParameter> implements JavaFxParameterStub {
  public JavaFxParameterStubImpl(final StubElement parent, final String name) {
    super(parent, JavaFxElementTypes.FORMAL_PARAMETER, name);
  }
}
