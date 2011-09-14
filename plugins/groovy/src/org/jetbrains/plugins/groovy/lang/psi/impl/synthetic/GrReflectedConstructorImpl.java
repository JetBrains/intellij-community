/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.psi.impl.light.LightElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrConstructor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;

/**
 * @author Max Medvedev
 */
public class GrReflectedConstructorImpl extends GrReflectedMethodImpl implements GrReflectedMethod, GrConstructor {
  public GrReflectedConstructorImpl(GrConstructor baseMethod, int optionalParams) {
    super(baseMethod, optionalParams);
  }

  @NotNull
  @Override
  public GrConstructor getBaseMethod() {
    return (GrConstructor)super.getBaseMethod();
  }

  public static GrReflectedMethod[] createReflectedMethods(GrConstructor method) {
    if (method instanceof LightElement) return GrReflectedMethod.EMPTY_ARRAY;

    final GrParameter[] parameters = method.getParameters();
    int count = 0;
    for (GrParameter parameter : parameters) {
      if (parameter.isOptional()) count++;
    }

    if (count == 0) return GrReflectedMethod.EMPTY_ARRAY;

    final GrReflectedMethod[] methods = new GrReflectedMethod[count + 1];
    for (int i = 0; i <= count; i++) {
      methods[i] = new GrReflectedConstructorImpl(method, count - i);
    }

    return methods;
  }

}
