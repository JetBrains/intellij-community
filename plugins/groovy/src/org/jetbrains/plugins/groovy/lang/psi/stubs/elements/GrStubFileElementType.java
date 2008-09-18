package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.lang.Language;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.io.StringRef;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFileStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GroovyFileStubBuilder;
import org.jetbrains.plugins.groovy.lang.psi.stubs.impl.GrFileStubImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrFullScriptNameIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrScriptClassNameIndex;

import java.io.IOException;

/**
 * @author ilyas
 */
public class GrStubFileElementType extends IStubFileElementType<GrFileStub> {

  public GrStubFileElementType(Language language) {
    super(language);
  }

  public StubBuilder getBuilder() {
    return new GroovyFileStubBuilder();
  }

  public String getExternalId() {
    return "groovy.FILE";
  }

  @Override
  public void indexStub(PsiFileStub stub, IndexSink sink) {
    super.indexStub(stub, sink);
  }

  @Override
  public void serialize(final GrFileStub stub, final StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getPackageName().toString());
    dataStream.writeName(stub.getName().toString());
    dataStream.writeBoolean(stub.isScript());
  }

  @Override
  public GrFileStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    StringRef packName = dataStream.readName();
    StringRef name = dataStream.readName();
    boolean isScript = dataStream.readBoolean();
    return new GrFileStubImpl(packName, name, isScript);
  }

  public void indexStub(GrFileStub stub, IndexSink sink) {
    String name = stub.getName().toString();
    if (stub.isScript() && name != null) {
      sink.occurrence(GrScriptClassNameIndex.KEY, name);
      sink.occurrence(GrFullScriptNameIndex.KEY, (stub.getPackageName().toString() + "." + name).hashCode());
    }
  }

}
