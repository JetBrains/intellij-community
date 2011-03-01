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
package org.jetbrains.plugins.groovy.extensions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public abstract class GroovyNamedArgumentProvider {

  public static final ExtensionPointName<GroovyNamedArgumentProvider> EP_NAME = ExtensionPointName.create("org.intellij.groovy.namedArgumentProvider");

  public abstract void getNamedArguments(@Nullable GrCall call, @NotNull PsiMethod method, Map<String, String[]> result);

  public static Map<String, String[]> getNamedArguments(@Nullable GrCall call, @NotNull PsiMethod method) {
    Map<String, String[]> namedArguments = new HashMap<String, String[]>();

    for (GroovyNamedArgumentProvider namedArgumentProvider : GroovyNamedArgumentProvider.EP_NAME.getExtensions()) {
      namedArgumentProvider.getNamedArguments(call, method, namedArguments);
    }

    return namedArguments;
  }

}
