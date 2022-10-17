// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.stubs.StubElement;
import com.intellij.util.BitUtil;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

public class GrParameterStub extends GrVariableStubBase<GrParameter> {

  private final int myFlags;

  public GrParameterStub(StubElement parent,
                         @NotNull StringRef name,
                         final String @NotNull [] annotations,
                         @Nullable String typeText,
                         int flags) {
    super(parent, GroovyStubElementTypes.PARAMETER, name, annotations, typeText);
    myFlags = flags;
  }

  public int getFlags() {
    return myFlags;
  }

  public static int encodeFlags(boolean hasInitializer, boolean isVarArgs) {
    return (hasInitializer ? 2 : 0) + (isVarArgs ? 1 : 0);
  }

  public static boolean hasInitializer(int flags) {
    return BitUtil.isSet(flags, 2);
  }

  public static boolean isVarRags(int flags) {
    return BitUtil.isSet(flags, 1);
  }
}
