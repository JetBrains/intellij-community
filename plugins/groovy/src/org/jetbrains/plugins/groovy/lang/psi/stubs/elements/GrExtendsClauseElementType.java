package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.EXTENDS_CLAUSE;
import org.jetbrains.plugins.groovy.lang.psi.GrStubElementType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrExtendsClauseImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrReferenceListStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.impl.GrReferenceListStubImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrDirectInheritorsIndex;

import java.io.IOException;

/**
 * @author ilyas
 */
public class GrExtendsClauseElementType extends GrStubElementType<GrReferenceListStub, GrExtendsClause> {

  public GrExtendsClauseElementType() {
    super("super class clause");
  }

  public GrExtendsClause createElement(ASTNode node) {
    return new GrExtendsClauseImpl(node);
  }

  public GrExtendsClause createPsi(GrReferenceListStub stub) {
    return new GrExtendsClauseImpl(stub);
  }

  public GrReferenceListStub createStub(GrExtendsClause psi, StubElement parentStub) {
    final GrCodeReferenceElement[] elements = psi.getReferenceElements();
    String[] refNames = ContainerUtil.map(elements, new Function<GrCodeReferenceElement, String>() {
      @Nullable
      public String fun(final GrCodeReferenceElement element) {
        return element.getReferenceName();
      }
    }, new String[elements.length]);

    return new GrReferenceListStubImpl(parentStub, EXTENDS_CLAUSE, refNames);
  }

  public void serialize(GrReferenceListStub stub, StubOutputStream dataStream) throws IOException {
    final String[] names = stub.getBaseClasses();
    dataStream.writeByte(names.length);
    for (String s : names) {
      dataStream.writeName(s);
    }
  }

  public GrReferenceListStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    final byte b = dataStream.readByte();
    final String[] names = new String[b];
    for (int i = 0; i < b; i++) {
      names[i] = dataStream.readName().toString();
    }
    return new GrReferenceListStubImpl(parentStub, EXTENDS_CLAUSE, names);
  }

  public void indexStub(GrReferenceListStub stub, IndexSink sink) {
    for (String name : stub.getBaseClasses()) {
      if (name != null) {
        sink.occurrence(GrDirectInheritorsIndex.KEY, name);
      }
    }
  }
}

