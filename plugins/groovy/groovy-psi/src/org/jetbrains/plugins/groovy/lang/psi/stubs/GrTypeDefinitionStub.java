// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import java.lang.ref.SoftReference;

import static com.intellij.reference.SoftReference.dereference;

public class GrTypeDefinitionStub extends StubBase<GrTypeDefinition> implements NamedStub<GrTypeDefinition> {
  private static final int ANONYMOUS = 0x01;
  private static final int INTERFACE = 0x02;
  private static final int ANNOTATION = 0x08;
  private static final int DEPRECATED_BY_DOC = 0x20;

  private final StringRef myName;
  private final @Nullable String myBaseClassName;
  private final StringRef myQualifiedName;
  private final String[] myAnnotations;
  private final byte myFlags;

  private volatile SoftReference<GrCodeReferenceElement> myStubBaseReference;

  public GrTypeDefinitionStub(StubElement parent,
                              final String name,
                              final @Nullable String baseClassName,
                              @NotNull IStubElementType elementType,
                              final String qualifiedName,
                              String[] annotations,
                              byte flags) {
    super(parent, elementType);
    myAnnotations = annotations;
    myName = StringRef.fromString(name);
    myBaseClassName = baseClassName;
    myQualifiedName = StringRef.fromString(qualifiedName);
    myFlags = flags;
  }

  public @Nullable String getBaseClassName() {
    return myBaseClassName;
  }

  public @Nullable GrCodeReferenceElement getBaseClassReference() {
    String baseClassName = getBaseClassName();
    if (baseClassName == null) return null;

    GrCodeReferenceElement reference = dereference(myStubBaseReference);
    if (reference == null) {
      reference = GroovyPsiElementFactory.getInstance(getProject()).createCodeReference(baseClassName, getPsi());
      myStubBaseReference = new SoftReference<>(reference);
    }
    return reference;
  }

  @Override
  public String getName() {
    return StringRef.toString(myName);
  }

  public String[] getAnnotations() {
    return myAnnotations;
  }

  public String getQualifiedName() {
    return StringRef.toString(myQualifiedName);
  }

  public boolean isAnnotationType() {
    return (myFlags & ANNOTATION) != 0;
  }

  public boolean isAnonymous() {
    return (myFlags & ANONYMOUS) != 0;
  }

  public boolean isInterface() {
    return (myFlags & INTERFACE) != 0;
  }

  public byte getFlags() {
    return myFlags;
  }

  public boolean isDeprecatedByDoc() {
    return (myFlags & DEPRECATED_BY_DOC) != 0;
  }

  public static byte buildFlags(GrTypeDefinition typeDefinition) {
    byte flags = 0;
    if (typeDefinition.isAnonymous()) {
      flags |= ANONYMOUS;
      assert typeDefinition instanceof GrAnonymousClassDefinition;
    }
    if (typeDefinition.isAnnotationType()) flags |= ANNOTATION;
    if (typeDefinition.isInterface()) flags |= INTERFACE;
    if (PsiImplUtil.isDeprecatedByDocTag(typeDefinition)) flags |= DEPRECATED_BY_DOC;
    return flags;
  }
}
