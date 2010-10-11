package org.jetbrains.javafx.lang.psi.impl.stubs.types;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.javafx.lang.psi.JavaFxClassDefinition;
import org.jetbrains.javafx.lang.psi.impl.JavaFxClassDefinitionImpl;
import org.jetbrains.javafx.lang.psi.impl.JavaFxQualifiedName;
import org.jetbrains.javafx.lang.psi.impl.stubs.JavaFxClassStubImpl;
import org.jetbrains.javafx.lang.psi.impl.stubs.index.JavaFxClassNameIndex;
import org.jetbrains.javafx.lang.psi.impl.stubs.index.JavaFxQualifiedNameIndex;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxClassStub;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxStubElementType;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxClassElementType extends JavaFxStubElementType<JavaFxClassStub, JavaFxClassDefinition> {
  public JavaFxClassElementType() {
    super("CLASS_DEFINITION");
  }

  @Override
  public PsiElement createElement(final ASTNode node) {
    return new JavaFxClassDefinitionImpl(node);
  }

  @Override
  public JavaFxClassDefinition createPsi(final JavaFxClassStub stub) {
    return new JavaFxClassDefinitionImpl(stub);
  }

  @Override
  public JavaFxClassStub createStub(final JavaFxClassDefinition psi, final StubElement parentStub) {
    return new JavaFxClassStubImpl(parentStub, psi.getQualifiedName());
  }

  public void serialize(final JavaFxClassStub stub, final StubOutputStream dataStream) throws IOException {
    JavaFxQualifiedName.serialize(stub.getQualifiedName(), dataStream);
  }

  public JavaFxClassStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    final JavaFxQualifiedName qualifiedName = JavaFxQualifiedName.deserialize(dataStream);
    return new JavaFxClassStubImpl(parentStub, qualifiedName);
  }

  public void indexStub(final JavaFxClassStub stub, final IndexSink sink) {
    final String name = stub.getName();
    if (name != null) {
      sink.occurrence(JavaFxClassNameIndex.KEY, name);
    }
    final JavaFxQualifiedName qualifiedName = stub.getQualifiedName();
    if (qualifiedName != null) {
      sink.occurrence(JavaFxQualifiedNameIndex.KEY, qualifiedName.toString());
    }
  }
}
