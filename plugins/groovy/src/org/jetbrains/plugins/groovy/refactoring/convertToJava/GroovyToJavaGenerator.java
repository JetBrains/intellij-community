// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
public final class GroovyToJavaGenerator {
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
