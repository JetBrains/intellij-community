/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 03.05.2007
 */
public class GroovyToJavaGenerator {
  private static final Map<String, String> typesToInitialValues = new HashMap<>();

  static {
    typesToInitialValues.put("boolean", "false");
    typesToInitialValues.put("int", "0");
    typesToInitialValues.put("short", "0");
    typesToInitialValues.put("long", "0L");
    typesToInitialValues.put("byte", "0");
    typesToInitialValues.put("char", "'c'");
    typesToInitialValues.put("double", "0D");
    typesToInitialValues.put("float", "0F");
    typesToInitialValues.put("void", "");
  }

  public static String getDefaultValueText(String typeCanonicalText) {
    final String result = typesToInitialValues.get(typeCanonicalText);
    if (result == null) return "null";
    return result;
  }

  public static String generateMethodStub(@NotNull PsiMethod method) {
    if (!(method instanceof GroovyPsiElement)) {
      return method.getText();
    }

    final ClassItemGenerator generator = new StubGenerator(new StubClassNameProvider(Collections.emptySet()));
    final StringBuilder buffer = new StringBuilder();
    if (method.isConstructor()) {
      generator.writeConstructor(buffer, method, false);
    }
    else {
      generator.writeMethod(buffer, method);
    }
    return buffer.toString();
  }
}
