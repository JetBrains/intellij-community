/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.reference.SoftReference;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

/**
 * @author ilyas
 */
public class GrTypeDefinitionStub extends StubBase<GrTypeDefinition> implements NamedStub<GrTypeDefinition> {
  private static final int ANONYMOUS = 0x01;
  private static final int INTERFACE = 0x02;
  private static final int ENUM = 0x04;
  private static final int ANNOTATION = 0x08;
  private static final int DEPRECATED_BY_DOC = 0x20;
  private static final int TRAIT = 0x40;

  private final StringRef myName;
  private final @Nullable String myBaseClassName;
  private final StringRef myQualifiedName;
  private final String[] myAnnotations;
  private final byte myFlags;

  private volatile SoftReference<GrCodeReferenceElement> myStubBaseReference;

  public GrTypeDefinitionStub(StubElement parent,
                                  final String name,
                                  @Nullable final String baseClassName,
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

  @Nullable
  public String getBaseClassName() {
    return myBaseClassName;
  }

  @Nullable
  public GrCodeReferenceElement getBaseClassReference() {
    String baseClassName = getBaseClassName();
    if (baseClassName == null) return null;

    GrCodeReferenceElement reference = SoftReference.dereference(myStubBaseReference);
    if (reference == null) {
      reference = GroovyPsiElementFactory.getInstance(getProject()).createReferenceElementFromText(baseClassName, getPsi());
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

  public boolean isEnum() {
    return (myFlags & ENUM) != 0;
  }

  public boolean isTrait() {
    return (myFlags & TRAIT) != 0;
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
    if (typeDefinition.isEnum()) flags |= ENUM;
    if (typeDefinition.isTrait()) flags |= TRAIT;
    if (PsiImplUtil.isDeprecatedByDocTag(typeDefinition)) flags |= DEPRECATED_BY_DOC;
    return flags;
  }

}
