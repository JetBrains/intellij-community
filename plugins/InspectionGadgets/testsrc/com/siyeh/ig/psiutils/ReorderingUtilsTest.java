// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.*;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.ThreeState;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

public class ReorderingUtilsTest extends LightJavaCodeInsightTestCase {
  private static final String PREFIX = """
    import java.util.Optional;
    import java.util.List;
    /** @noinspection all*/
    class X {Object test(Object obj, String str, int x, int y, String[] arr, Optional<String> opt, List<String> list, Integer boxed) { return\s""";
  private static final String SUFFIX = ";} static Object nullNull(Object obj) {return obj == null ? null : obj.hashCode();}}";
  private static final String SELECTION_START = "/*<*/";
  private static final String SELECTION_END = "/*>*/";

  public void testSimple() {
    checkCanBeReordered("x > 0 && /*<*/x < 10/*>*/", ThreeState.YES);
    checkCanBeReordered("arr == null && x > 10 && /*<*/y < 5/*>*/", ThreeState.YES);
  }

  public void testTrueFirst() {
    checkCanBeReordered("x > 10 && x > 0 && /*<*/x > arr.length/*>*/", ThreeState.UNSURE);
    checkCanBeReordered("(x > 10 || x < 20) && /*<*/x > arr.length/*>*/", ThreeState.UNSURE);
  }

  public void testNpe() {
    checkCanBeReordered("obj != null && /*<*/obj.hashCode() > 10/*>*/", ThreeState.NO);
    checkCanBeReordered("obj == null || /*<*/obj.hashCode() > 10/*>*/", ThreeState.NO);
    checkCanBeReordered("arr != null && /*<*/obj.hashCode() > 10/*>*/", ThreeState.UNSURE);
    checkCanBeReordered("boxed != null && /*<*/boxed > 10/*>*/", ThreeState.NO);
    checkCanBeReordered("x != null && /*<*/x > 10/*>*/", ThreeState.YES); // compilation error at null-check actually
  }

  public void testCast() {
    checkCanBeReordered("obj instanceof String && /*<*/((String)obj).isEmpty()/*>*/", ThreeState.NO);
    checkCanBeReordered("obj instanceof String && /*<*/nullNull((String)obj) == null/*>*/", ThreeState.NO);
    checkCanBeReordered("obj instanceof Integer && /*<*/((Number)obj).intValue() == 0/*>*/", ThreeState.NO);
  }
  
  public void testContract() {
    checkCanBeReordered("new Object().equals(obj) && /*<*/obj.hashCode() == 0/*>*/", ThreeState.NO);
    checkCanBeReordered("nullNull(obj) != null && /*<*/obj.hashCode() == 0/*>*/", ThreeState.NO);
    checkCanBeReordered("nullNull(obj) == null || /*<*/obj.hashCode() == 0/*>*/", ThreeState.NO);
    checkCanBeReordered("nullNull(nullNull(obj)) == null || /*<*/obj.hashCode() == 0/*>*/", ThreeState.NO);
  }

  public void testArrayBounds() {
    checkCanBeReordered("x >= 0 && /*<*/arr[x].isEmpty()/*>*/", ThreeState.NO);
    checkCanBeReordered("x < arr.length && /*<*/arr[x].isEmpty()/*>*/", ThreeState.NO);
    checkCanBeReordered("x >= 0 && x < arr.length && /*<*/arr[x].isEmpty()/*>*/", ThreeState.NO);
    checkCanBeReordered("y >= 0 && /*<*/arr[x].isEmpty()/*>*/", ThreeState.UNSURE);
    checkCanBeReordered("x > 0 && /*<*/arr[x].isEmpty()/*>*/", ThreeState.NO);
    // Not supported
    checkCanBeReordered("x > 0 && /*<*/arr[x-1].isEmpty()/*>*/", ThreeState.UNSURE);
  }
  
  public void testStringBounds() {
    checkCanBeReordered("x >= 0 && /*<*/str.charAt(x) == 'a'/*>*/", ThreeState.NO);
    checkCanBeReordered("y >= 0 && /*<*/str.charAt(x) == 'a'/*>*/", ThreeState.UNSURE);
    checkCanBeReordered("x < str.length() && /*<*/str.charAt(x) == 'a'/*>*/", ThreeState.NO);
    checkCanBeReordered("x <= str.length() && /*<*/str.substring(x) == 'a'/*>*/", ThreeState.NO);
    checkCanBeReordered("x <= str.charAt(100) && /*<*/str.substring(x) == 'a'/*>*/", ThreeState.UNSURE);
    // Not supported
    checkCanBeReordered("x < str.length() && /*<*/str.substring(x) == 'a'/*>*/", ThreeState.UNSURE);
  }
  
  public void testListBounds() {
    checkCanBeReordered("x >= 0 && /*<*/list.get(x).isEmpty()/*>*/", ThreeState.NO);
    checkCanBeReordered("x < list.size() && /*<*/list.get(x).isEmpty()/*>*/", ThreeState.NO);
    checkCanBeReordered("list.size() > x && /*<*/list.get(x).isEmpty()/*>*/", ThreeState.NO);
  }

  public void testOptional() {
    checkCanBeReordered("opt.isPresent() && /*<*/opt.get().isEmpty()/*>*/", ThreeState.NO);
  }
  
  public void testTernary() {
    checkCanBeReordered("x > y ? /*<*/y/*>*/ : x", ThreeState.YES);
    checkCanBeReordered("str == null ? \"\" : /*<*/str.trim()/*>*/", ThreeState.NO);
    checkCanBeReordered("str != null ? /*<*/str.trim()/*>*/ : \"\"", ThreeState.NO);
    checkCanBeReordered("str != null ? \"\" : /*<*/str.trim()/*>*/", ThreeState.UNSURE);
    checkCanBeReordered("str == null ? /*<*/str.trim()/*>*/ : \"\"", ThreeState.UNSURE);
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_9_ANNOTATED;
  }

  private void checkCanBeReordered(@Language(value = "JAVA", prefix = PREFIX, suffix = SUFFIX) String expressionText,
                                   ThreeState expectedResult) {
    String file = PREFIX + expressionText + SUFFIX;
    PsiJavaFile javaFile = (PsiJavaFile)PsiFileFactory.getInstance(getProject()).createFileFromText("X.java", JavaFileType.INSTANCE, file);
    PsiCodeBlock body = javaFile.getClasses()[0].getMethods()[0].getBody();
    assertNotNull(body);
    PsiExpression expression = ((PsiReturnStatement)body.getStatements()[0]).getReturnValue();
    assertNotNull(expression);
    int startOffset = expressionText.indexOf(SELECTION_START);
    assertTrue(startOffset >= 0);
    int endOffset = expressionText.indexOf(SELECTION_END);
    assertTrue(endOffset >= 0);
    int expressionStart = expression.getTextRange().getStartOffset();
    PsiExpression subExpression = CodeInsightUtil.findExpressionInRange(javaFile, startOffset + SELECTION_START.length() + expressionStart,
                                                                        endOffset + expressionStart);
    assertNotNull(subExpression);
    assertSame(expressionText, expectedResult, ReorderingUtils.canExtract(expression, subExpression));
  }
}