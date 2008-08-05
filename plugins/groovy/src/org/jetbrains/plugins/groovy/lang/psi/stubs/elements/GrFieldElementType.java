package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import org.jetbrains.plugins.groovy.lang.psi.GrStubElementType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrFieldImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFieldStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.impl.GrFieldStubImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrFieldNameIndex;

import java.io.IOException;

/**
 * @author ilyas
 */
public class GrFieldElementType extends GrStubElementType<GrFieldStub, GrField> {

  public GrFieldElementType() {
    super("field");
  }

  public PsiElement createElement(ASTNode node) {
    return new GrFieldImpl(node);
  }

  public GrField createPsi(GrFieldStub stub) {
    return new GrFieldImpl(stub);
  }

  public GrFieldStub createStub(GrField psi, StubElement parentStub) {
    return new GrFieldStubImpl(parentStub, StringRef.fromString(psi.getName()), false);
  }

  public void serialize(GrFieldStub stub, StubOutputStream dataStream) throws IOException {
    serializeFieldStub(stub, dataStream);
  }

  public GrFieldStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    return deserializeFieldStub(dataStream, parentStub);
  }

  public void indexStub(GrFieldStub stub, IndexSink sink) {
    indexFieldStub(stub, sink);
  }

  /*
   * ****************************************************************************************************************
   */

  static void serializeFieldStub(GrFieldStub stub, StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    dataStream.writeBoolean(stub.isEnumConstant());
  }

  static GrFieldStub deserializeFieldStub(StubInputStream dataStream, StubElement parentStub) throws IOException {
    StringRef ref = dataStream.readName();
    boolean isEnumConstant = dataStream.readBoolean();
    return new GrFieldStubImpl(parentStub, ref, isEnumConstant);
  }

  static void indexFieldStub(GrFieldStub stub, IndexSink sink) {
    String name = stub.getName();
    if (name != null) {
      sink.occurrence(GrFieldNameIndex.KEY, name);
    }
  }
}
