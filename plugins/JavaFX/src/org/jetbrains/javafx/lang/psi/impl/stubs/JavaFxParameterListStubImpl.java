package org.jetbrains.javafx.lang.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxParameterList;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxParameterListStub;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxParameterListStubImpl extends StubBase<JavaFxParameterList> implements JavaFxParameterListStub {
  public JavaFxParameterListStubImpl(final StubElement parent) {
    super(parent, JavaFxElementTypes.PARAMETER_LIST);
  }
}
