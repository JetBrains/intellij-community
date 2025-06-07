// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

public class GrImportStatementStub extends StubBase<GrImportStatement> implements StubElement<GrImportStatement> {
  private static final byte STATIC_MASK = 0x1;
  private static final byte ON_DEMAND_MASK = 0x2;

  private final String myFqn;
  private final String myAliasName;
  private final byte myFlags;

  public GrImportStatementStub(StubElement parent,
                               @NotNull IStubElementType elementType,
                               @Nullable String fqn,
                               @Nullable String aliasName,
                               byte flags) {
    super(parent, elementType);
    myFqn = fqn;
    myAliasName = aliasName;
    myFlags = flags;
  }

  public @Nullable String getFqn() {
    return myFqn;
  }

  public @Nullable String getAliasName() {
    return myAliasName;
  }

  public boolean isStatic() {
    return (myFlags & STATIC_MASK) != 0;
  }

  public boolean isOnDemand() {
    return (myFlags & ON_DEMAND_MASK) != 0;
  }

  public byte getFlags() {
    return myFlags;
  }

  public static byte buildFlags(boolean isStatic, boolean isOnDemand) {
    return (byte)((isStatic ? 1 : 0) * STATIC_MASK +
                  (isOnDemand ? 1 : 0) * ON_DEMAND_MASK);
  }
}
