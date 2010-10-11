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
import org.jetbrains.javafx.lang.parser.JavaFxStubElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxVariableDeclaration;
import org.jetbrains.javafx.lang.psi.impl.JavaFxQualifiedName;
import org.jetbrains.javafx.lang.psi.impl.JavaFxVariableDeclarationImpl;
import org.jetbrains.javafx.lang.psi.impl.stubs.JavaFxVariableStubImpl;
import org.jetbrains.javafx.lang.psi.impl.stubs.index.JavaFxQualifiedNameIndex;
import org.jetbrains.javafx.lang.psi.impl.stubs.index.JavaFxVariableNameIndex;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxStubElementType;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxVariableStub;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxVariableElementType extends JavaFxStubElementType<JavaFxVariableStub, JavaFxVariableDeclaration> {
  public JavaFxVariableElementType() {
    super("VARIABLE_DECLARATION");
  }

  @Override
  public PsiElement createElement(final ASTNode node) {
    return new JavaFxVariableDeclarationImpl(node);
  }

  @Override
  public JavaFxVariableDeclaration createPsi(final JavaFxVariableStub stub) {
    return new JavaFxVariableDeclarationImpl(stub);
  }

  @Override
  public JavaFxVariableStub createStub(final JavaFxVariableDeclaration psi, final StubElement parentStub) {
    return new JavaFxVariableStubImpl(parentStub, psi.getQualifiedName(), psi.getName());
  }

  public void serialize(final JavaFxVariableStub stub, final StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    JavaFxQualifiedName.serialize(stub.getQualifiedName(), dataStream);
  }

  public JavaFxVariableStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    final String name = StringRef.toString(dataStream.readName());
    final JavaFxQualifiedName qualifiedName = JavaFxQualifiedName.deserialize(dataStream);
    return new JavaFxVariableStubImpl(parentStub, qualifiedName, name);
  }

  @Override
  public boolean shouldCreateStub(final ASTNode node) {
    final ASTNode parent = node.getTreeParent();
    if (parent == null) {
      return false;
    }
    final IElementType parentType = parent.getElementType();
    return (parentType == JavaFxStubElementTypes.FILE) || (parentType == JavaFxElementTypes.CLASS_DEFINITION);
  }

  public void indexStub(final JavaFxVariableStub stub, final IndexSink sink) {
    final String name = stub.getName();
    if (name != null) {
      sink.occurrence(JavaFxVariableNameIndex.KEY, name);
    }
    final JavaFxQualifiedName qualifiedName = stub.getQualifiedName();
    if (qualifiedName != null) {
      sink.occurrence(JavaFxQualifiedNameIndex.KEY, qualifiedName.toString());
    }
  }
}
