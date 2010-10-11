package org.jetbrains.javafx.lang.psi.impl.stubs.types;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxParameterList;
import org.jetbrains.javafx.lang.psi.impl.JavaFxParameterListImpl;
import org.jetbrains.javafx.lang.psi.impl.stubs.JavaFxParameterListStubImpl;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxParameterListStub;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxStubElementType;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxParameterListElementType extends JavaFxStubElementType<JavaFxParameterListStub, JavaFxParameterList> {
  public JavaFxParameterListElementType() {
    super("PARAMETER_LIST");
  }

  @Override
  public PsiElement createElement(ASTNode node) {
    return new JavaFxParameterListImpl(node);
  }

  @Override
  public JavaFxParameterList createPsi(JavaFxParameterListStub stub) {
    return new JavaFxParameterListImpl(stub);
  }

  @Override
  public JavaFxParameterListStub createStub(JavaFxParameterList psi, StubElement parentStub) {
    return new JavaFxParameterListStubImpl(parentStub);
  }

  @Override
  public boolean shouldCreateStub(ASTNode node) {
    final ASTNode treeParent = node.getTreeParent();
    return treeParent != null && treeParent.getTreeParent().getElementType() == JavaFxElementTypes.FUNCTION_DEFINITION;
  }

  public void serialize(JavaFxParameterListStub stub, StubOutputStream dataStream) throws IOException {
  }

  public JavaFxParameterListStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new JavaFxParameterListStubImpl(parentStub);
  }

  public void indexStub(JavaFxParameterListStub stub, IndexSink sink) {
  }
}
