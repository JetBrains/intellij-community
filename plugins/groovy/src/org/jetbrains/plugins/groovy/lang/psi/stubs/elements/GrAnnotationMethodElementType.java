package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import org.jetbrains.plugins.groovy.lang.psi.GrStubElementType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAnnotationMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrAnnotationMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrAnnotationMethodStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.impl.GrAnnotationMethodStubImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnnotationMethodNameIndex;

import java.io.IOException;

/**
 * @author ilyas
 */
public class GrAnnotationMethodElementType extends GrStubElementType<GrAnnotationMethodStub, GrAnnotationMethod> {

  public GrAnnotationMethodElementType() {
    super("annotation method");
  }

  @Override
  public GrAnnotationMethod createElement(ASTNode node) {
    return new GrAnnotationMethodImpl(node);
  }

  @Override
  public GrAnnotationMethod createPsi(GrAnnotationMethodStub stub) {
    return new GrAnnotationMethodImpl(stub);
  }

  @Override
  public GrAnnotationMethodStub createStub(final GrAnnotationMethod psi, final StubElement parentStub) {
    return new GrAnnotationMethodStubImpl(parentStub, StringRef.fromString(psi.getName()));
  }

  @Override
  public void serialize(GrAnnotationMethodStub stub, StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
  }

  @Override
  public GrAnnotationMethodStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    StringRef ref = dataStream.readName();
    return new GrAnnotationMethodStubImpl(parentStub, ref);
  }

  @Override
  public void indexStub(GrAnnotationMethodStub stub, IndexSink sink) {
    String name = stub.getName();
    if (name != null) {
      sink.occurrence(GrAnnotationMethodNameIndex.KEY, name);
    }
  }

}
