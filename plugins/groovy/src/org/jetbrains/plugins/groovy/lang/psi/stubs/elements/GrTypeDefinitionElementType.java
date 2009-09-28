package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrStubElementType;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.impl.GrTypeDefinitionStubImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnnotatedMemberIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnonymousClassIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrFullClassNameIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrShortClassNameIndex;

import java.io.IOException;
import java.util.List;

/**
 * @author ilyas
 */
public abstract class GrTypeDefinitionElementType<TypeDef extends GrTypeDefinition>
  extends GrStubElementType<GrTypeDefinitionStub, TypeDef> {

  public GrTypeDefinitionElementType(@NotNull String debugName) {
    super(debugName);
  }

  public GrTypeDefinitionStub createStub(TypeDef psi, StubElement parentStub) {
    String[] superClassNames = psi.getSuperClassNames();
    final byte flags = GrTypeDefinitionStubImpl.buildFlags(psi);
    return new GrTypeDefinitionStubImpl(parentStub, psi.getName(), superClassNames, this, psi.getQualifiedName(), getAnnotationNames(psi),
                                        flags);
  }

  private static String[] getAnnotationNames(GrTypeDefinition psi) {
    List<String> annoNames = CollectionFactory.arrayList();
    final PsiModifierList modifierList = psi.getModifierList();
    if (modifierList instanceof GrModifierList) {
      for (GrAnnotation annotation : ((GrModifierList)modifierList).getAnnotations()) {
        final GrCodeReferenceElement element = annotation.getClassReference();
        if (element != null) {
          final String annoShortName = StringUtil.getShortName(element.getText()).trim();
          if (StringUtil.isNotEmpty(annoShortName)) {
            annoNames.add(annoShortName);
          }
        }
      }
    }
    return annoNames.toArray(new String[annoNames.size()]);
  }

  public void serialize(GrTypeDefinitionStub stub, StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    dataStream.writeName(stub.getQualifiedName());
    dataStream.writeByte(stub.getFlags());
    writeStringArray(dataStream, stub.getSuperClassNames());
    writeStringArray(dataStream, stub.getAnnotations());
  }

  private static void writeStringArray(StubOutputStream dataStream, String[] names) throws IOException {
    dataStream.writeByte(names.length);
    for (String name : names) {
      dataStream.writeName(name);
    }
  }

  public GrTypeDefinitionStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    String name = StringRef.toString(dataStream.readName());
    String qname = StringRef.toString(dataStream.readName());
    byte flags = dataStream.readByte();
    String[] superClasses = readStringArray(dataStream);
    String[] annos = readStringArray(dataStream);
    return new GrTypeDefinitionStubImpl(parentStub, name, superClasses, this, qname, annos, flags);
  }

  private static String[] readStringArray(StubInputStream dataStream) throws IOException {
    byte supersNumber = dataStream.readByte();
    String[] superClasses = new String[supersNumber];
    for (int i = 0; i < supersNumber; i++) {
      superClasses[i] = StringRef.toString(dataStream.readName());
    }
    return superClasses;
  }

  public void indexStub(GrTypeDefinitionStub stub, IndexSink sink) {
    if (stub.isAnonymous()) {
      final String[] classNames = stub.getSuperClassNames();
      if (classNames.length != 1) return;
      final String baseClassName = classNames[0];
      if (baseClassName != null) {
        final String shortName = PsiNameHelper.getShortClassName(baseClassName);
        sink.occurrence(GrAnonymousClassIndex.KEY, shortName);
      }
    }
    else {
      String shortName = stub.getName();
      if (shortName != null) {
        sink.occurrence(GrShortClassNameIndex.KEY, shortName);
      }
      final String fqn = stub.getQualifiedName();
      if (fqn != null) {
        sink.occurrence(GrFullClassNameIndex.KEY, fqn.hashCode());
      }
    }

    for (String annName : stub.getAnnotations()) {
      if (annName != null) {
        sink.occurrence(GrAnnotatedMemberIndex.KEY, annName);
      }
    }
  }
}
