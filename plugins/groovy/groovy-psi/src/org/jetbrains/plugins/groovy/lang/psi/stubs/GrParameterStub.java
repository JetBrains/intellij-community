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

import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.BitUtil;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

/**
 * @author peter
 */
public class GrParameterStub extends StubBase<GrParameter> implements NamedStub<GrParameter> {
  private final StringRef myName;
  private final String[] myAnnotations;
  private final String myTypeText;
  private final int myFlags;

  public GrParameterStub(StubElement parent,
                         StringRef name,
                         final String[] annotations,
                         String typeText, 
                         int flags) {
    super(parent, GroovyElementTypes.PARAMETER);
    myName = name;
    myAnnotations = annotations;
    myTypeText = typeText;
    myFlags = flags;
  }

  @Override
  @NotNull
  public String getName() {
    return StringRef.toString(myName);
  }

  public String[] getAnnotations() {
    return myAnnotations;
  }

  public String getTypeText() {
    return myTypeText;
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
