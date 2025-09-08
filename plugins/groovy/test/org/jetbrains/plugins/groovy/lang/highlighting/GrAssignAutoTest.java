// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.util.containers.ContainerUtil;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LibraryLightProjectDescriptor;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.util.Slow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Character and char are skipped intentionally.
 * Double and Double[] are skipped intentionally.
 * Current spec https://github.com/apache/groovy/blob/master/src/spec/doc/core-differences-java.adoc#conversions
 * Bug: https://issues.apache.org/jira/browse/GROOVY-7557
 */
@Slow
public class GrAssignAutoTest extends GrHighlightingTestBase {

  private static final String CS = """
    import groovy.transform.CompileStatic
    """;

  private static final List<String> types = List.of(
    "boolean", "int", "double", "String", "BigDecimal", "BigInteger", "List", "Object", "Thread",
    "List<BigDecimal>", "List<BigInteger>", "List<Integer>", "List<String>", "List<Object>", "List<Thread>", "boolean[]",
    "int[]", "double[]", "String[]", "Integer[]", "List[]", "Object[]", "Thread[]", "short", "byte", "Set", "Set<String>",
    "Set<Integer>", "Set<Object>", "Set<Thread>");

  private static final List<String> booleanValues = List.of("true", "false", "(Boolean)true", "false as Boolean");
  private static final List<String> byteValues = List.of("(byte) 0", "-1 as Byte", "(byte)(+2)", "126", "-127");
  private static final List<String> shortValues = List.of("(short) 0", "-1 as Short", "(Short)(+2)", "-32768", "32767");
  private static final List<String> intValues = List.of("0", "-1", "+1", "(Integer)(+2)", "1i", "-1I", "+1 as int", "+32768", "2E3", "-2E4", "0b101101101101", "0246", "0xffff", "-0x77", "1234_5678");
  private static final List<String> longValues = List.of("0l", "-1L", "+2L", "(Long)(+2)", "+1 as long", "1234_5678_9012_3456L", "0b101101101101L", "0246L", "0xffffL", "-0x77L", "0x7fff_ffff_ffff_ffffL", "new Long(\"123\")");
  private static final List<String> bigIntegerValues = List.of("BigInteger.valueOf(1)", "-1 as BigInteger", "+2G", "-0G", "034G", "1234_5678_9012_3456G", "0b101101101101G", "0246G", "0xffffG", "-0x77G");
  private static final List<String> floatValues = List.of("1.1f", "-1.1f", "+0.002f", "-0F", "034F", "5_132.12F", "0b101101101101f", "0246F", "0xffffF", "1 as Float", "(float) 1.1");
  private static final List<String> doubleValues = List.of("1.1d", "-1.1d", "+0.002d", "-0D", "034D", "12_345_132.12D", "0b101101101101d", "0246d", "0xffffD", "1 as Double", "(double) 1.1");
  private static final List<String> bigDecimalValues = List.of("1.1", "-1.2", "+0.002", "-0.0", "034.0G", "12_345_132.12g", "1 as BigDecimal", "(BigDecimal) 1.1");
  private static final List<String> objectValues = List.of("new Object()", "new Thread()", "\"str\"", "null");
  private static final List<String> listValues = List.of("[]", "[1]", "[0L]", "[1.1]", "[1.2f]", "[\"str\"]", "new ArrayList<>()", "[new Object()]", "[new Thread()]");
  private static final List<String> voidValues = List.of("print(\"\")", "(Void)null");
  private static final List<String> values = ContainerUtil.concat(booleanValues, byteValues, shortValues, intValues,
                                                                  longValues, bigIntegerValues, floatValues, doubleValues, bigDecimalValues, objectValues, listValues, voidValues);

  private static final List<List<String>> typesXTypes = vectorProduct(types, types);
  private static final List<List<String>> valuesXTypes = vectorProduct(values, types);

  private static List<List<String>> vectorProduct(List<String> vector1, List<String> vector2) {
    List<List<String>> result = new ArrayList<>();
    for (String a : vector1) {
      for (String b : vector2) {
        result.add(List.of(a, b));
      }
    }
    return result;
  }

  @Override
  @NotNull
  protected LightProjectDescriptor getProjectDescriptor() {
    return new LibraryLightProjectDescriptor(GroovyProjectDescriptors.LIB_GROOVY_2_5) {
      @Override
      public Sdk getSdk() {
        return IdeaTestUtil.getMockJdk18();
      }
    };
  }

  @Override
  public InspectionProfileEntry[] getCustomInspections() { return new InspectionProfileEntry[]{new GroovyAssignabilityCheckInspection()}; }

  public void testParameterToLocal() {
    doTest("""
             @CompileStatic
             void method%3$s(%1$s param) {
               %2$s local = param
             }
             """,
           typesXTypes,
           List.of(
             "List<BigDecimal> -> boolean[]", "List<BigDecimal> -> double[]", "List<BigDecimal> -> String[]", "List<BigDecimal> -> Object[]", "List<BigInteger> -> boolean[]", "List<BigInteger> -> String[]", "List<BigInteger> -> Object[]",
             "List<Integer> -> boolean[]", "List<Integer> -> int[]", "List<Integer> -> double[]", "List<Integer> -> String[]", "List<Integer> -> Integer[]", "List<Integer> -> Object[]",
             "List<String> -> boolean[]", "List<String> -> String[]", "List<String> -> Object[]",
             "List<Object> -> boolean[]", "List<Object> -> String[]", "List<Object> -> Object[]",
             "List<Thread> -> boolean[]", "List<Thread> -> String[]", "List<Thread> -> Object[]", "List<Thread> -> Thread[]",
             "Set<String> -> boolean[]", "Set<String> -> String[]", "Set<String> -> Object[]",
             "Set<Integer> -> boolean[]", "Set<Integer> -> int[]", "Set<Integer> -> double[]", "Set<Integer> -> String[]", "Set<Integer> -> Integer[]", "Set<Integer> -> Object[]",
             "Set<Object> -> boolean[]", "Set<Object> -> String[]", "Set<Object> -> Object[]",
             "Set<Thread> -> boolean[]", "Set<Thread> -> String[]", "Set<Thread> -> Object[]", "Set<Thread> -> Thread[]"),
           List.of(
             "boolean -> int", "boolean -> double", "boolean -> short", "boolean -> byte", "boolean[] -> String[]", "boolean[] -> Object[]", "boolean[] -> int[]", "boolean[] -> double[]",
             "int[] -> boolean[]", "int[] -> String[]", "int[] -> Integer[]", "int[] -> Object[]",
             "double[] -> boolean[]", "double[] -> Integer[]", "double[] -> Object[]", "double[] -> String[]",
             "Integer[] -> String[]", "Integer[] -> boolean[]", "Integer[] -> int[]", "Integer[] -> double[]",
             "String[] -> boolean[]",
             "List[] -> String[]", "List[] -> boolean[]",
             "Object[] -> String[]", "Object[] -> boolean[]",
             "Thread[] -> String[]", "Thread[] -> boolean[]"));
  }

  public void testParameterToReturn() {
    doTest("""
             @CompileStatic
             %2$s method%3$s(%1$s param) {
               return param
             }
             """,
           typesXTypes,
           List.of(),
           List.of(
             "boolean -> int", "boolean -> double", "boolean -> short", "boolean -> byte",
             "boolean[] -> int[]", "boolean[] -> double[]", "boolean[] -> String[]", "boolean[] -> Object[]",
             "int[] -> boolean[]", "int[] -> String[]", "int[] -> Integer[]", "int[] -> Object[]",
             "double[] -> boolean[]", "double[] -> String[]", "double[] -> Integer[]", "double[] -> Object[]",
             "String[] -> boolean[]",
             "Integer[] -> boolean[]", "Integer[] -> int[]", "Integer[] -> double[]", "Integer[] -> String[]",
             "List[] -> boolean[]", "List[] -> String[]",
             "Object[] -> boolean[]", "Object[] -> String[]",
             "Thread[] -> boolean[]", "Thread[] -> String[]"));
  }

  public void testParameterMethodCall() {
    doTest("""
             def bar%3$s(%2$s param) {
             }
             
             @CompileStatic
             def method%3$s(%1$s param) {
               bar%3$s(param)
             }
             """,
           typesXTypes,
           List.of("int -> double[]", "short -> int[]", "short -> double[]", "byte -> int[]", "byte -> double[]", "short -> Integer[]", "byte -> Integer[]"),
           List.of("BigDecimal -> double", "BigDecimal -> double[]"));
  }

  public void testReturnAssignValue() {
    doTest("""
             @CompileStatic
             %2$s method%3$s() {
               return %1$s
             }
             """,
           valuesXTypes,
           List.of(),
           List.of("true -> int", "true -> double", "true -> short", "true -> byte", "false -> int", "false -> double", "false -> short",
                   "false -> byte", "(Void)null -> int", "(Void)null -> double", "(Void)null -> BigDecimal", "(Void)null -> BigInteger",
                   "(Void)null -> List", "(Void)null -> Thread", "(Void)null -> List<BigDecimal>", "(Void)null -> List<BigInteger>",
                   "(Void)null -> List<Integer>", "(Void)null -> List<String>", "(Void)null -> List<Object>", "(Void)null -> List<Thread>",
                   "(Void)null -> boolean[]", "(Void)null -> int[]", "(Void)null -> double[]", "(Void)null -> String[]",
                   "(Void)null -> Integer[]", "(Void)null -> List[]", "(Void)null -> Object[]", "(Void)null -> Thread[]",
                   "(Void)null -> short", "(Void)null -> byte", "(Void)null -> Set", "(Void)null -> Set<String>",
                   "(Void)null -> Set<Integer>", "(Void)null -> Set<Object>", "(Void)null -> Set<Thread>",
                   "[1] -> List<Object>", "[0L] -> List<Object>", "[1.1] -> List<Object>", "[1.2f] -> List<Object>", "[\"str\"] -> List<Object>",
                   "[new Thread()] -> List<Object>"));
  }

  public void testLocalAssignValue() {
    doTest("""
             @CompileStatic
             void method%3$s() {
               %2$s param = %1$s
             }
             """,
           valuesXTypes,
           List.of("[0L] -> BigInteger", "(Void)null -> Object", "[1] -> int", "[1] -> double", "[0L] -> double", "[1.2f] -> double",
                   "[\"str\"] -> int", "[\"str\"] -> double", "[\"str\"] -> short", "[\"str\"] -> byte", "new ArrayList<>() -> boolean[]",
                   "new ArrayList<>() -> String[]", "new ArrayList<>() -> Object[]"),
           List.of(
             "[1] -> List<Object>", "[1] -> Set", "[1] -> Set<Integer>", "[1] -> Set<Object>",
             "[0L] -> List<Object>", "[0L] -> Set", "[0L] -> Set<Object>",
             "[1.1] -> List<Object>", "[1.1] -> Set", "[1.1] -> Set<Object>",
             "[1.2f] -> List<Object>", "[1.2f] -> Set", "[1.2f] -> Set<Object>",
             "[\"str\"] -> List<Object>", "[\"str\"] -> Set", "[\"str\"] -> Set<String>", "[\"str\"] -> Set<Object>",
             "[new Object()] -> Set", "[new Object()] -> Set<Object>",
             "[new Thread()] -> List<Object>", "[new Thread()] -> Set", "[new Thread()] -> Set<Object>", "[new Thread()] -> Set<Thread>",
             "[1.1] -> BigDecimal"));
  }

  private void doTest(String body, List<List<String>> arguments, List<String> wrongFalseByIdea, List<String> wrongTrueByIdea) {
    List<String> falseDiff = new ArrayList<>();
    List<String> trueDiff = new ArrayList<>();

    Set<String> falseIssues = new HashSet<>(wrongFalseByIdea);
    Set<String> trueIssues = new HashSet<>(wrongTrueByIdea);

    Set<Integer> shellErrors = shellTest(body, arguments);
    Set<Integer> ideaErrors = ideaTest(body, arguments);
    for (int i = 0; i < arguments.size(); i++) {
      List<String> argument = arguments.get(i);
      boolean ideaTest = ideaErrors.contains(i);
      boolean shellTest = shellErrors.contains(i);
      if (ideaTest != shellTest) {
        Set<String> activeIssues = ideaTest ? trueIssues : falseIssues;
        List<String> activeDiff = ideaTest ? trueDiff : falseDiff;
        String pair = argument.get(0) + " -> " + argument.get(1);
        if (!activeIssues.remove(pair)) {
          activeDiff.add(pair);
        }
      }
    }
    assertEmpty(falseDiff.toString(), falseDiff);
    assertEmpty(trueDiff.toString(), trueDiff);
    assertEmpty(falseIssues.toString(), falseIssues);
    assertEmpty(trueIssues.toString(), trueIssues);
  }

  private Set<Integer> ideaTest(String body, List<List<String>> arguments) {
    StringBuilder text = new StringBuilder(CS);
    for (int i = 0; i < arguments.size(); i++) {
      List<String> argument = arguments.get(i);
      text.append(String.format(body, argument.get(0), argument.get(1), i));
    }

    final int offsetLen = StringUtil.countNewLines(CS);
    final int lineCount = StringUtil.countNewLines(body);

    final HashSet<Integer> res = new HashSet<>();
    myFixture.configureByText("_.groovy", text.toString());
    myFixture.enableInspections(getCustomInspections());
    List<HighlightInfo> highlighting = myFixture.doHighlighting(HighlightSeverity.ERROR);
    Document document = myFixture.getEditor().getDocument();
    for (HighlightInfo info : highlighting) {
      res.add((document.getLineNumber(info.getStartOffset()) - offsetLen) / lineCount);
    }
    return res;
  }

  private static Set<Integer> shellTest(String body, List<List<String>> arguments) {
    final HashSet<Integer> res = new HashSet<>();
    final GroovyShell shell = new GroovyShell();
    for (int i = 0; i < arguments.size(); i++) {
      List<String> argument = arguments.get(i);
      String text = CS + String.format(body, argument.get(0), argument.get(1), i);
      try {
        shell.evaluate(text);
      }
      catch (MultipleCompilationErrorsException ignored) {
        res.add(i);
      }
    }
    return res;
  }
}