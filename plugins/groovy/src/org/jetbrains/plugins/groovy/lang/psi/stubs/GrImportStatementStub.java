/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

/**
 * Created by Max Medvedev on 11/29/13
 */
public class GrImportStatementStub extends StubBase<GrImportStatement> implements StubElement<GrImportStatement> {
  private static final byte STATIC_MASK = 0x1;
  private static final byte ON_DEMAND_MASK = 0x2;

  private final byte myFlags;
  private final String myReferenceText;
  private final String myAliasName;

  public GrImportStatementStub(StubElement parent,
                               IStubElementType elementType,
                               @Nullable String referenceText,
                               @Nullable String aliasName,
                               byte flags) {
    super(parent, elementType);

    myFlags = flags;

    myReferenceText = referenceText;
    myAliasName = aliasName;
  }

  public static byte buildFlags(boolean isStatic, boolean isOnDemand) {
    return (byte)((isStatic ? 1 : 0) * STATIC_MASK +
                  (isOnDemand ? 1 : 0) * ON_DEMAND_MASK);
  }

  public boolean isStatic() {
    return (myFlags & STATIC_MASK) != 0;
  }

  public boolean isOnDemand() {
    return (myFlags & ON_DEMAND_MASK) != 0;
  }

  @Nullable
  public String getReferenceText() {
    return myReferenceText;
  }

  @Nullable
  public String getAliasName() {
    return myAliasName;
  }

  public byte getFlags() {
    return myFlags;
  }
}
