package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrStubElementType;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrShortClassNameIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrSuperClassIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrFullClassNameIndex;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.stubs.GrTypeDefinitionStubImpl;

import java.io.IOException;

/**
 * @author ilyas
 */
public abstract class GrTypeDefinitionElementType<TypeDef extends GrTypeDefinition> extends GrStubElementType<GrTypeDefinitionStub, TypeDef> {

  public GrTypeDefinitionElementType(@NotNull String debugName) {
    super(debugName);
  }

  public GrTypeDefinitionStub createStub(TypeDef psi, StubElement parentStub) {
    String[] superClassNames = psi.getSuperClassNames();
    return new GrTypeDefinitionStubImpl(psi.getName(), parentStub, superClassNames, this, psi.getQualifiedName());
  }

  public void serialize(GrTypeDefinitionStub stub, StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    dataStream.writeName(stub.getQualifiedName());
    String[] names = stub.getSuperClassNames();
    dataStream.writeByte(names.length);
    for (String name : names) {
      dataStream.writeName(name);
    }
  }

  public GrTypeDefinitionStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    String name = StringRef.toString(dataStream.readName());
    String qname = StringRef.toString(dataStream.readName());
    byte supersNumber = dataStream.readByte();
    String[] superClasses = new String[supersNumber];
    for (int i = 0; i < supersNumber; i++) {
      superClasses[i] = StringRef.toString(dataStream.readName());
    }
    return new GrTypeDefinitionStubImpl(name, parentStub, superClasses, this, qname);
  }

  public void indexStub(GrTypeDefinitionStub stub, IndexSink sink) {
    String shortName = stub.getName();
    if (shortName != null) {
      sink.occurrence(GrShortClassNameIndex.KEY, shortName);
    }
    final String fqn = stub.getQualifiedName();
    if (fqn != null) {
      sink.occurrence(GrFullClassNameIndex.KEY, fqn.hashCode());
    }

    for (String s : stub.getSuperClassNames()) {
      sink.occurrence(GrSuperClassIndex.KEY, s);
    }
  }
}
