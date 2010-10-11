package org.jetbrains.javafx.lang.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxImportList;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxImportListStub;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxImportListStubImpl extends StubBase<JavaFxImportList> implements JavaFxImportListStub {
  public JavaFxImportListStubImpl(final StubElement parent) {
    super(parent, JavaFxElementTypes.IMPORT_LIST);
  }
}
