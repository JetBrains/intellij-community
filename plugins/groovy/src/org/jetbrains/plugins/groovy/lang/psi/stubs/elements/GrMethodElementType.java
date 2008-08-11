package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrStubElementType;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrMethodStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.impl.GrMethodStubImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnnotatedMemberIndex;
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
    final GrModifierList modifiers = psi.getModifierList();
    final GrAnnotation[] annotations = modifiers.getAnnotations();
    String[] annNames = ContainerUtil.map(annotations, new Function<GrAnnotation, String>() {
      @Nullable
      public String fun(final GrAnnotation grAnnotation) {
        final GrCodeReferenceElement element = grAnnotation.getClassReference();
        if (element == null) return null;
        return element.getReferenceName();
      }
    }, new String[annotations.length]);

    return new GrMethodStubImpl(parentStub, StringRef.fromString(psi.getName()), annNames);
  }

  public void serialize(GrMethodStub stub, StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    final String[] annotations = stub.getAnnotations();
    dataStream.writeByte(annotations.length);
    for (String s : annotations) {
      dataStream.writeName(s);
    }
  }

  public GrMethodStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    StringRef ref = dataStream.readName();
    final byte b = dataStream.readByte();
    final String[] annNames = new String[b];
    for (int i = 0; i < b; i++) {
      annNames[i] = dataStream.readName().toString();
    }
    return new GrMethodStubImpl(parentStub, ref, annNames);
  }

  public void indexStub(GrMethodStub stub, IndexSink sink) {
    String name = stub.getName();
    if (name != null) {
      sink.occurrence(GrMethodNameIndex.KEY, name);
    }
    for (String annName : stub.getAnnotations()) {
      if (annName != null) {
        sink.occurrence(GrAnnotatedMemberIndex.KEY, annName);
      }
    }
  }
}
