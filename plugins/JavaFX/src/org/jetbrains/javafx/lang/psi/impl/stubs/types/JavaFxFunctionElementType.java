package org.jetbrains.javafx.lang.psi.impl.stubs.types;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import org.jetbrains.javafx.lang.psi.JavaFxFunctionDefinition;
import org.jetbrains.javafx.lang.psi.impl.JavaFxFunctionDefinitionImpl;
import org.jetbrains.javafx.lang.psi.impl.JavaFxQualifiedName;
import org.jetbrains.javafx.lang.psi.impl.stubs.JavaFxFunctionStubImpl;
import org.jetbrains.javafx.lang.psi.impl.stubs.index.JavaFxFunctionNameIndex;
import org.jetbrains.javafx.lang.psi.impl.stubs.index.JavaFxQualifiedNameIndex;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxFunctionStub;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxStubElementType;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxFunctionElementType extends JavaFxStubElementType<JavaFxFunctionStub, JavaFxFunctionDefinition> {
  public JavaFxFunctionElementType() {
    super("FUNCTION_DEFINITION");
  }

  @Override
  public PsiElement createElement(final ASTNode node) {
    return new JavaFxFunctionDefinitionImpl(node);
  }

  @Override
  public JavaFxFunctionDefinition createPsi(final JavaFxFunctionStub stub) {
    return new JavaFxFunctionDefinitionImpl(stub);
  }

  @Override
  public JavaFxFunctionStub createStub(final JavaFxFunctionDefinition psi, final StubElement parentStub) {
    return new JavaFxFunctionStubImpl(parentStub, psi.getQualifiedName(), psi.getName());
  }
  public void serialize(final JavaFxFunctionStub stub, final StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    JavaFxQualifiedName.serialize(stub.getQualifiedName(), dataStream);
  }

  public JavaFxFunctionStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    final String name = StringRef.toString(dataStream.readName());
    final JavaFxQualifiedName qualifiedName = JavaFxQualifiedName.deserialize(dataStream);
    return new JavaFxFunctionStubImpl(parentStub, qualifiedName, name);
  }

  public void indexStub(final JavaFxFunctionStub stub, final IndexSink sink) {
    final String name = stub.getName();
    if (name != null) {
      sink.occurrence(JavaFxFunctionNameIndex.KEY, name);
    }
    final JavaFxQualifiedName qualifiedName = stub.getQualifiedName();
    if (qualifiedName != null) {
      sink.occurrence(JavaFxQualifiedNameIndex.KEY, qualifiedName.toString());
    }
  }
}
