package org.jetbrains.javafx.lang.psi.impl.stubs.types;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.io.StringRef;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxParameter;
import org.jetbrains.javafx.lang.psi.impl.JavaFxParameterImpl;
import org.jetbrains.javafx.lang.psi.impl.stubs.JavaFxParameterStubImpl;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxParameterStub;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxStubElementType;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxParameterElementType extends JavaFxStubElementType<JavaFxParameterStub, JavaFxParameter> {
  public JavaFxParameterElementType() {
    super("FORMAL_PARAMETER");
  }

  @Override
  public PsiElement createElement(ASTNode node) {
    return new JavaFxParameterImpl(node);
  }

  @Override
  public JavaFxParameter createPsi(JavaFxParameterStub stub) {
    return new JavaFxParameterImpl(stub);
  }

  @Override
  public JavaFxParameterStub createStub(JavaFxParameter psi, StubElement parentStub) {
    return new JavaFxParameterStubImpl(parentStub, psi.getName());
  }

  public void serialize(JavaFxParameterStub stub, StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
  }

  public JavaFxParameterStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    final String name = StringRef.toString(dataStream.readName());
    return new JavaFxParameterStubImpl(parentStub, name);
  }

  @Override
  public boolean shouldCreateStub(ASTNode node) {
    final ASTNode parent = node.getTreeParent();
    if (parent == null) {
      return false;
    }
    final IElementType parentType = parent.getElementType();
    if (parentType != JavaFxElementTypes.PARAMETER_LIST) {
      return false;
    }
    final ASTNode pParent = parent.getTreeParent().getTreeParent();
    return pParent != null && pParent.getElementType() == JavaFxElementTypes.FUNCTION_DEFINITION;
  }

  public void indexStub(JavaFxParameterStub stub, IndexSink sink) {
  }
}
