package org.jetbrains.javafx.lang.psi.impl.stubs.types;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.javafx.lang.psi.JavaFxPackageDefinition;
import org.jetbrains.javafx.lang.psi.impl.JavaFxPackageDefinitionImpl;
import org.jetbrains.javafx.lang.psi.impl.JavaFxQualifiedName;
import org.jetbrains.javafx.lang.psi.impl.stubs.JavaFxPackageDefinitionStubImpl;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxPackageDefinitionStub;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxStubElementType;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxPackageDefinitionElementType extends JavaFxStubElementType<JavaFxPackageDefinitionStub, JavaFxPackageDefinition> {
  public JavaFxPackageDefinitionElementType() {
    super("PACKAGE_DEFINITION");
  }

  @Override
  public PsiElement createElement(final ASTNode node) {
    return new JavaFxPackageDefinitionImpl(node);
  }

  @Override
  public JavaFxPackageDefinition createPsi(final JavaFxPackageDefinitionStub stub) {
    return new JavaFxPackageDefinitionImpl(stub);
  }

  @Override
  public JavaFxPackageDefinitionStub createStub(final JavaFxPackageDefinition psi, final StubElement parentStub) {
    return new JavaFxPackageDefinitionStubImpl(parentStub, psi.getQualifiedName());
  }

  public void serialize(final JavaFxPackageDefinitionStub stub, final StubOutputStream dataStream) throws IOException {
    JavaFxQualifiedName.serialize(stub.getQualifiedName(), dataStream);
  }

  public JavaFxPackageDefinitionStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    final JavaFxQualifiedName qualifiedName = JavaFxQualifiedName.deserialize(dataStream);
    return new JavaFxPackageDefinitionStubImpl(parentStub, qualifiedName);
  }

  public void indexStub(final JavaFxPackageDefinitionStub stub, final IndexSink sink) {
  }
}
