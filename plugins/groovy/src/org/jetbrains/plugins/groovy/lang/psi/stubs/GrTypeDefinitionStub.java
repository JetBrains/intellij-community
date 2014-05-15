/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author ilyas
 */
public class GrTypeDefinitionStub extends StubBase<GrTypeDefinition> implements NamedStub<GrTypeDefinition> {
  private static final int ANONYMOUS = 0x01;
  private static final int INTERFACE = 0x02;
  private static final int ENUM = 0x04;
  private static final int ANNOTATION = 0x08;
  private static final int IS_IN_QUALIFIED_NEW = 0x10;
  private static final int DEPRECATED_BY_DOC = 0x20;
  private static final int TRAIT = 0x40;

  private final StringRef myName;
  private final String[] mySuperClasses;
  private final StringRef myQualifiedName;
  private final String[] myAnnotations;
  private final byte myFlags;

  public GrTypeDefinitionStub(StubElement parent,
                                  final String name,
                                  final String[] supers,
                                  @NotNull IStubElementType elementType,
                                  final String qualifiedName,
                                  String[] annotations,
                                  byte flags) {
    super(parent, elementType);
    myAnnotations = annotations;
    myName = StringRef.fromString(name);
    mySuperClasses = supers;
    myQualifiedName = StringRef.fromString(qualifiedName);
    myFlags = flags;
  }

  public String[] getSuperClassNames() {
    return mySuperClasses;
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

  public boolean isAnonymousInQualifiedNew() {
    return (myFlags & IS_IN_QUALIFIED_NEW) != 0;
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
      if (((GrAnonymousClassDefinition)typeDefinition).isInQualifiedNew()) {
        flags |= IS_IN_QUALIFIED_NEW;
      }
    }
    if (typeDefinition.isAnnotationType()) flags |= ANNOTATION;
    if (typeDefinition.isInterface()) flags |= INTERFACE;
    if (typeDefinition.isEnum()) flags |= ENUM;
    if (typeDefinition.isTrait()) flags |= TRAIT;
    if (PsiImplUtil.isDeprecatedByDocTag(typeDefinition)) flags |= DEPRECATED_BY_DOC;
    return flags;
  }

}
