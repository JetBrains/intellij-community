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
import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.stubs.elements.GrMethodElementType;

/**
 * @author ilyas
 */
public class GrMethodStub extends StubBase<GrMethod> implements NamedStub<GrMethod> {
  public static final byte IS_DEPRECATED_BY_DOC_TAG = 0b1;
  public static final byte HAS_BLOCK = 0b10;

  private final StringRef myName;
  private final String[] myAnnotations;
  private final String[] myNamedParameters;
  private final String myTypeText;
  private final byte myFlags;

  public GrMethodStub(StubElement parent,
                      StringRef name,
                      final String[] annotations,
                      @NotNull final String[] namedParameters,
                      @NotNull GrMethodElementType elementType,
                      @Nullable String typeText,
                      byte flags) {
    super(parent, elementType);
    myName = name;
    myAnnotations = annotations;
    myNamedParameters = namedParameters;
    myTypeText = typeText;
    myFlags = flags;
  }

  @Override
  @NotNull public String getName() {
    return StringRef.toString(myName);
  }

  public String[] getAnnotations() {
    return myAnnotations;
  }

  @NotNull
  public String[] getNamedParameters() {
    return myNamedParameters;
  }

  @Nullable
  public String getTypeText() {
    return myTypeText;
  }

  public boolean isDeprecatedByDoc() {
    return (myFlags & IS_DEPRECATED_BY_DOC_TAG) != 0;
  }

  public boolean hasBlock() {
    return (myFlags & HAS_BLOCK) != 0;
  }

  public static byte buildFlags(GrMethod method) {
    byte f = 0;

    if (PsiImplUtil.isDeprecatedByDocTag(method)) {
      f |= IS_DEPRECATED_BY_DOC_TAG;
    }

    if (method.hasBlock()) {
      f |= HAS_BLOCK;
    }

    return f;
  }

  public byte getFlags() {
    return myFlags;
  }
}
