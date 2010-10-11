package org.jetbrains.javafx.lang.psi.impl.stubs.types;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.javafx.lang.psi.JavaFxImportList;
import org.jetbrains.javafx.lang.psi.impl.JavaFxImportListImpl;
import org.jetbrains.javafx.lang.psi.impl.stubs.JavaFxImportListStubImpl;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxImportListStub;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxStubElementType;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxImportListElementType extends JavaFxStubElementType<JavaFxImportListStub, JavaFxImportList> {
  public JavaFxImportListElementType() {
    super("IMPORT_LIST");
  }

  @Override
  public PsiElement createElement(final ASTNode node) {
    return new JavaFxImportListImpl(node);
  }

  @Override
  public JavaFxImportList createPsi(final JavaFxImportListStub stub) {
    return new JavaFxImportListImpl(stub);
  }

  @Override
  public JavaFxImportListStub createStub(final JavaFxImportList psi, final StubElement parentStub) {
    return new JavaFxImportListStubImpl(parentStub);
  }

  public void serialize(final JavaFxImportListStub stub, final StubOutputStream dataStream) throws IOException {
  }

  public JavaFxImportListStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    return new JavaFxImportListStubImpl(parentStub);
  }

  public void indexStub(final JavaFxImportListStub stub, final IndexSink sink) {
  }
}
