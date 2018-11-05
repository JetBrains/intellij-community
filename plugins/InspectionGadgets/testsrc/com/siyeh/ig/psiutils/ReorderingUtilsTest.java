// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.psiutils;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.*;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.util.ThreeState;
import org.intellij.lang.annotations.Language;

public class ReorderingUtilsTest extends LightCodeInsightTestCase {
  private static final String PREFIX = "import java.util.Optional;\n" +
                                       "import java.util.List;\n" +
                                       "/** @noinspection all*/\n" +
                                       "class X {boolean test(Object obj, String str, int x, int y, String[] arr, " +
                                       "Optional<String> opt, List<String> list) { return ";
  @SuppressWarnings("UnnecessarySemicolon") 
  private static final String SUFFIX = ";} static Object nullNull(Object obj) {return obj == null ? null : obj.hashCode();}}";

  public void testSimple() {
    checkCanBeReordered("x > 0 && x < 10", 1, ThreeState.YES);
    checkCanBeReordered("arr == null && x > 10 && y < 5", 2, ThreeState.YES);
  }

  public void testTrueFirst() {
    checkCanBeReordered("x > 10 && x > 0 && x > arr.length", 2, ThreeState.UNSURE);
    checkCanBeReordered("(x > 10 || x < 20) && x > arr.length", 1, ThreeState.UNSURE);
  }

  public void testNpe() {
    checkCanBeReordered("obj != null && obj.hashCode() > 10", 1, ThreeState.NO);
    checkCanBeReordered("obj == null || obj.hashCode() > 10", 1, ThreeState.NO);
    checkCanBeReordered("arr != null && obj.hashCode() > 10", 1, ThreeState.UNSURE);
  }

  public void testCast() {
    checkCanBeReordered("obj instanceof String && ((String)obj).isEmpty()", 1, ThreeState.NO);
    checkCanBeReordered("obj instanceof String && test(null, (String)obj, 0,0, null, Optional.empty())", 1, ThreeState.NO);
    checkCanBeReordered("obj instanceof Integer && ((Number)obj).intValue() == 0", 1, ThreeState.NO);
  }
  
  public void testContract() {
    checkCanBeReordered("new Object().equals(obj) && obj.hashCode() == 0", 1, ThreeState.NO);
    checkCanBeReordered("nullNull(obj) != null && obj.hashCode() == 0", 1, ThreeState.NO);
    checkCanBeReordered("nullNull(obj) == null || obj.hashCode() == 0", 1, ThreeState.NO);
    checkCanBeReordered("nullNull(nullNull(obj)) == null || obj.hashCode() == 0", 1, ThreeState.NO);
  }

  public void testArrayBounds() {
    checkCanBeReordered("x >= 0 && arr[x].isEmpty()", 1, ThreeState.NO);
    checkCanBeReordered("x < arr.length && arr[x].isEmpty()", 1, ThreeState.NO);
    checkCanBeReordered("x >= 0 && x < arr.length && arr[x].isEmpty()", 2, ThreeState.NO);
    checkCanBeReordered("y >= 0 && arr[x].isEmpty()", 1, ThreeState.UNSURE);
    checkCanBeReordered("x > 0 && arr[x].isEmpty()", 1, ThreeState.NO);
    // Not supported
    checkCanBeReordered("x > 0 && arr[x-1].isEmpty()", 1, ThreeState.UNSURE);
  }
  
  public void testStringBounds() {
    checkCanBeReordered("x >= 0 && str.charAt(x) == 'a'", 1, ThreeState.NO);
    checkCanBeReordered("y >= 0 && str.charAt(x) == 'a'", 1, ThreeState.UNSURE);
    checkCanBeReordered("x < str.length() && str.charAt(x) == 'a'", 1, ThreeState.NO);
    checkCanBeReordered("x <= str.length() && str.substring(x) == 'a'", 1, ThreeState.NO);
    // Not supported
    checkCanBeReordered("x < str.length() && str.substring(x) == 'a'", 1, ThreeState.UNSURE);
  }
  
  public void testListBounds() {
    checkCanBeReordered("x >= 0 && list.get(x).isEmpty()", 1, ThreeState.NO);
    checkCanBeReordered("x < list.size() && list.get(x).isEmpty()", 1, ThreeState.NO);
    checkCanBeReordered("list.size() > x && list.get(x).isEmpty()", 1, ThreeState.NO);
  }

  public void testOptional() {
    checkCanBeReordered("opt.isPresent() && opt.get().isEmpty()", 1, ThreeState.NO);
  }

  private static void checkCanBeReordered(@Language(value = "JAVA", prefix = PREFIX, suffix = SUFFIX) String expressionText,
                                          int operand,
                                          ThreeState expectedResult) {
    String file = PREFIX + expressionText + SUFFIX;
    PsiJavaFile javaFile = (PsiJavaFile)PsiFileFactory.getInstance(getProject()).createFileFromText("X.java", JavaFileType.INSTANCE, file);
    PsiCodeBlock body = javaFile.getClasses()[0].getMethods()[0].getBody();
    assertNotNull(body);
    PsiPolyadicExpression expression = (PsiPolyadicExpression)((PsiReturnStatement)body.getStatements()[0]).getReturnValue();
    assertNotNull(expression);
    assertSame(expressionText, expectedResult, ReorderingUtils.canExtract(expression, expression.getOperands()[operand]));
  }
}