package org.jetbrains.javafx.lang.psi.impl.stubs.types;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxSignature;
import org.jetbrains.javafx.lang.psi.impl.JavaFxSignatureImpl;
import org.jetbrains.javafx.lang.psi.impl.stubs.JavaFxSignatureStubImpl;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxSignatureStub;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxStubElementType;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * @author: Alexey.Ivanov
 */
public class JavaFxSignatureElementType extends JavaFxStubElementType<JavaFxSignatureStub, JavaFxSignature>  {
  public JavaFxSignatureElementType() {
    super("FUNCTION_SIGNATURE");
  }

  @Override
  public PsiElement createElement(ASTNode node) {
    return new JavaFxSignatureImpl(node);
  }

  @Override
  public JavaFxSignature createPsi(JavaFxSignatureStub stub) {
    return new JavaFxSignatureImpl(stub);
  }

  @Override
  public JavaFxSignatureStub createStub(JavaFxSignature psi, StubElement parentStub) {
    return new JavaFxSignatureStubImpl(parentStub);
  }

  @Override
  public boolean shouldCreateStub(ASTNode node) {
    final ASTNode parent = node.getTreeParent();
    return parent != null && parent.getElementType() == JavaFxElementTypes.FUNCTION_DEFINITION;
  }

  @Override
  public void serialize(JavaFxSignatureStub stub, StubOutputStream dataStream) throws IOException {
  }

  @Override
  public JavaFxSignatureStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new JavaFxSignatureStubImpl(parentStub);
  }

  @Override
  public void indexStub(JavaFxSignatureStub stub, IndexSink sink) {
  }
}
