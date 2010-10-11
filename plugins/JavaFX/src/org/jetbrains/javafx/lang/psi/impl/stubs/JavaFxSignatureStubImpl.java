package org.jetbrains.javafx.lang.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxSignature;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxSignatureStub;

/**
 * Created by IntelliJ IDEA.
 * @author: Alexey.Ivanov
 */
public class JavaFxSignatureStubImpl extends StubBase<JavaFxSignature> implements JavaFxSignatureStub {
  public JavaFxSignatureStubImpl(final StubElement parent) {
    super(parent, JavaFxElementTypes.FUNCTION_SIGNATURE);
  }
}
