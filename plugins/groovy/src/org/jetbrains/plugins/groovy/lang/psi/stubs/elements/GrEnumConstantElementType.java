package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.StringRef;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrStubElementType;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.enumConstant.GrEnumConstantImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFieldStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.impl.GrFieldStubImpl;

import java.io.IOException;

/**
 * @author ilyas
 */
public class GrEnumConstantElementType extends GrStubElementType<GrFieldStub, GrEnumConstant> {

  public GrEnumConstantElementType() {
    super("Enumeration constant");
  }

  public PsiElement createElement(ASTNode node) {
    return new GrEnumConstantImpl(node);
  }

  public GrEnumConstant createPsi(GrFieldStub stub) {
    return new GrEnumConstantImpl(stub);
  }

  @Override
  public GrFieldStub createStub(GrEnumConstant psi, StubElement parentStub) {
     final GrModifierList list = psi.getModifierList();
    String[] annNames;
    if (list == null) {
      annNames = new String[0];
    }
    else {
      annNames = ContainerUtil.map(list.getAnnotations(), new Function<GrAnnotation, String>() {
        public String fun(final GrAnnotation grAnnotation) {
          final GrCodeReferenceElement element = grAnnotation.getClassReference();
          if (element == null) return null;
          return element.getReferenceName();
        }
      }, new String[0]);
    }
    return new GrFieldStubImpl(parentStub, StringRef.fromString(psi.getName()), true, annNames, GroovyElementTypes.ENUM_CONSTANT);
  }

  public void serialize(GrFieldStub stub, StubOutputStream dataStream) throws IOException {
    serializeFieldStub(stub, dataStream);
  }

  public GrFieldStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    return GrFieldElementType.deserializeFieldStub(dataStream, parentStub);
  }

  protected static void serializeFieldStub(GrFieldStub stub, StubOutputStream dataStream) throws IOException {
    GrFieldElementType.serializeFieldStub(stub, dataStream);
  }

  public void indexStub(GrFieldStub stub, IndexSink sink) {
    GrFieldElementType.indexFieldStub(stub, sink);
  }
}
