// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.util.LiteralUtilKt;
import org.jetbrains.plugins.groovy.util.BaseTest;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_LONG;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.JAVA_MATH_BIG_INTEGER;
import static org.junit.Assert.*;

public class LiteralTest extends GroovyLatestTest implements BaseTest {
  @Test
  public void literalValue() {
    LinkedHashMap<String, Number> data = new LinkedHashMap<>(19);
    data.put("42", 42);
    data.put("0b101010", 0b101010);
    data.put("0B101010", 0B101010);
    //noinspection OctalInteger
    data.put("052", 052);
    data.put("0x2a", 0x2a);
    data.put("0X2a", 0X2a);
    data.put("42i", 42);
    data.put("42I", 42);
    //noinspection LongLiteralEndingWithLowercaseL
    data.put("42l", 42l);
    data.put("42L", 42L);
    data.put("42g", new BigInteger("42"));
    data.put("42G", new BigInteger("42"));
    data.put("42f", 42f);
    data.put("42F", 42F);
    data.put("42d", 42d);
    data.put("42D", 42D);
    data.put("42.42", new BigDecimal("42.42"));
    data.put("42.0g", new BigDecimal("42.0"));
    data.put("42.0G", new BigDecimal("42.0"));
    for (Map.Entry<String, Number> entry : data.entrySet()) {
      GrLiteral literal = elementUnderCaret(entry.getKey(), GrLiteral.class);
      assertEquals(entry.getKey(), entry.getValue(), literal.getValue());
    }
  }

  @Test
  public void zeroLiterals() {
    List<String> data =
      List.of("0", "0b0", "0B0", "00", "0x0", "0X0", "0i", "0I", "0l", "0L", "0g", "0G", "0f", "0F", "0d", "0D", "0.0g", "0.0G");
    for (String string : data) {
      assertTrue(LiteralUtilKt.isZero(elementUnderCaret(string, GrLiteral.class)));
    }
  }

  @Test
  public void integerLiteralsWithoutSuffix() {
    LinkedHashMap<String, String> data = new LinkedHashMap<>(16);
    data.put("2147483647", JAVA_LANG_INTEGER);
    data.put("0b1111111111111111111111111111111", JAVA_LANG_INTEGER);
    data.put("017777777777", JAVA_LANG_INTEGER);
    data.put("0x7FFFFFFF", JAVA_LANG_INTEGER);
    data.put("2147483648", JAVA_LANG_LONG);
    data.put("0b10000000000000000000000000000000", JAVA_LANG_LONG);
    data.put("020000000000", JAVA_LANG_LONG);
    data.put("0x80000000", JAVA_LANG_LONG);
    data.put("9223372036854775807", JAVA_LANG_LONG);
    data.put("0b111111111111111111111111111111111111111111111111111111111111111", JAVA_LANG_LONG);
    data.put("0777777777777777777777", JAVA_LANG_LONG);
    data.put("0x7FFFFFFFFFFFFFFF", JAVA_LANG_LONG);
    data.put("9223372036854775808", JAVA_MATH_BIG_INTEGER);
    data.put("0b1000000000000000000000000000000000000000000000000000000000000000", JAVA_MATH_BIG_INTEGER);
    data.put("01000000000000000000000", JAVA_MATH_BIG_INTEGER);
    data.put("0x8000000000000000", JAVA_MATH_BIG_INTEGER);
    for (Map.Entry<String, String> entry : data.entrySet()) {
      String literalText = entry.getKey();
      String literalType = entry.getValue();
      GrLiteral literal = elementUnderCaret(literalText, GrLiteral.class);
      assertEquals(literalText, literalType, literal.getType().getCanonicalText());
      Object value = literal.getValue();
      assertNotNull(literalText, value);
      assertEquals(value.getClass().getName(), literalType);
    }
  }
}
