package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import org.jetbrains.plugins.groovy.lang.psi.GrStubElementType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrMethodStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.impl.GrMethodStubImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrMethodNameIndex;

import java.io.IOException;

/**
 * @author ilyas
 */
public class GrMethodElementType extends GrStubElementType<GrMethodStub, GrMethod> {

  public GrMethodElementType() {
    super("method definition");
  }

  public GrMethod createElement(ASTNode node) {
    return new GrMethodImpl(node);
  }

  public GrMethod createPsi(GrMethodStub stub) {
    return new GrMethodImpl(stub);
  }

  public GrMethodStub createStub(GrMethod psi, StubElement parentStub) {
    return new GrMethodStubImpl(parentStub, StringRef.fromString(psi.getName()));
  }

  public void serialize(GrMethodStub stub, StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
  }

  public GrMethodStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    StringRef ref = dataStream.readName();
    return new GrMethodStubImpl(parentStub, ref);
  }

  public void indexStub(GrMethodStub stub, IndexSink sink) {
    String name = stub.getName();
    if (name != null) {
      sink.occurrence(GrMethodNameIndex.KEY, name);
    }
  }

}
