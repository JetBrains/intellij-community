// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LibraryLightProjectDescriptor
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.util.Slow

import static org.assertj.core.api.Assertions.assertThat
/**
 * Character and char are skipped intentionally.
 * Double and Double[] are skipped intentionally.
 * Current spec https://github.com/apache/groovy/blob/master/src/spec/doc/core-differences-java.adoc#conversions
 * Bug: https://issues.apache.org/jira/browse/GROOVY-7557
 */
@CompileStatic
@Slow
class GrAssignAutoTest extends GrHighlightingTestBase {

  private static final String CS = '''\
    import groovy.transform.CompileStatic
  '''

  private static final List<String> types = [
    'boolean', 'int', 'double', 'String', 'BigDecimal', 'BigInteger', 'List', 'Object', 'Thread',
    'List<BigDecimal>', 'List<BigInteger>', 'List<Integer>', 'List<String>', 'List<Object>', 'List<Thread>', 'boolean[]',
    'int[]', 'double[]', 'String[]', 'Integer[]', 'List[]', 'Object[]', 'Thread[]', 'short', 'byte', 'Set', 'Set<String>',
    'Set<Integer>', 'Set<Object>', 'Set<Thread>'
  ]

  private static final List<String> booleanValues = ['true', 'false', '(Boolean)true', 'false as Boolean']
  private static final List<String> byteValues = ['(byte) 0', '-1 as Byte', '(byte)(+2)', '126', '-127']
  private static final List<String> shortValues = ['(short) 0', '-1 as Short', '(Short)(+2)', '-32768', '32767']
  private static final List<String> intValues = ['0', '-1', '+1', '(Integer)(+2)', '1i', '-1I', '+1 as int', '+32768', '2E3', '-2E4', '0b101101101101', '0246', '0xffff', '-0x77', '1234_5678']
  private static final List<String> longValues = ['0l', '-1L', '+2L', '(Long)(+2)', '+1 as long', '1234_5678_9012_3456L', '0b101101101101L', '0246L', '0xffffL', '-0x77L', '0x7fff_ffff_ffff_ffffL', 'new Long("123")']
  private static final List<String> bigIntegerValues = ['BigInteger.valueOf(1)', '-1 as BigInteger', '+2G', '-0G', '034G', '1234_5678_9012_3456G', '0b101101101101G', '0246G', '0xffffG', '-0x77G']
  private static final List<String> floatValues = ['1.1f', '-1.1f', '+0.002f', '-0F', '034F', '5_132.12F', '0b101101101101f', '0246F', '0xffffF', '1 as Float', '(float) 1.1']
  private static final List<String> doubleValues = ['1.1d', '-1.1d', '+0.002d', '-0D', '034D', '12_345_132.12D', '0b101101101101d', '0246d', '0xffffD', '1 as Double', '(double) 1.1']
  private static final List<String> bigDecimalValues = ['1.1', '-1.2', '+0.002', '-0.0', '034.0G', '12_345_132.12g', '1 as BigDecimal', '(BigDecimal) 1.1']
  private static final List<String> objectValues = ['new Object()', 'new Thread()', '"str"', 'null']
  private static final List<String> listValues = ['[]', '[1]', '[0L]', '[1.1]', '[1.2f]', '["str"]', 'new ArrayList<>()', '[new Object()]', '[new Thread()]']
  private static final List<String> voidValues = ['print("")', '(Void)null']
  private static final List<String> values = booleanValues +
                                             byteValues +
                                             shortValues +
                                             intValues +
                                             longValues +
                                             bigIntegerValues +
                                             floatValues +
                                             doubleValues +
                                             bigDecimalValues +
                                             objectValues +
                                             listValues +
                                             voidValues

  private static final List<List<String>> typesXTypes = vectorProduct(types, types)
  private static final List<List<String>> valuesXTypes = vectorProduct(values, types)

  private static List<List<String>> vectorProduct(List<String> vector1, List<String> vector2) {
    return vector1.collectMany { arg1 -> vector2.collect { arg2 -> [arg1, arg2] } }
  }

  @Override
  @NotNull
  protected LightProjectDescriptor getProjectDescriptor() {
    return new LibraryLightProjectDescriptor(GroovyProjectDescriptors.LIB_GROOVY_2_5) {
      @Override
      Sdk getSdk() {
        return IdeaTestUtil.getMockJdk18()
      }
    }
  }

  @Override
  InspectionProfileEntry[] getCustomInspections() { [new GroovyAssignabilityCheckInspection()] as InspectionProfileEntry[] }

  void testParameterToLocal() {
    doTest '''
        @CompileStatic
        void method%3$s(%1$s param) {
          %2$s local = param
        }
        ''',
           typesXTypes,
           [],
           ["boolean -> int", "boolean -> double", "boolean -> short", "boolean -> byte",
            "List -> boolean[]", "List -> int[]", "List -> double[]", "List -> String[]", "List -> Integer[]", "List -> List[]", "List -> Object[]", "List -> Thread[]",
            "List<BigDecimal> -> int[]", "List<BigDecimal> -> Integer[]", "List<BigDecimal> -> List[]", "List<BigDecimal> -> Thread[]",
            "List<BigInteger> -> int[]", "List<BigInteger> -> double[]", "List<BigInteger> -> Integer[]", "List<BigInteger> -> List[]", "List<BigInteger> -> Thread[]",
            "List<Integer> -> List[]", "List<Integer> -> Thread[]",
            "List<String> -> int[]", "List<String> -> double[]", "List<String> -> Integer[]", "List<String> -> List[]", "List<String> -> Thread[]",
            "List<Object> -> int[]", "List<Object> -> double[]", "List<Object> -> Integer[]", "List<Object> -> List[]", "List<Object> -> Thread[]",
            "List<Thread> -> int[]", "List<Thread> -> double[]", "List<Thread> -> Integer[]", "List<Thread> -> List[]",
            "boolean[] -> int[]", "boolean[] -> double[]",
            "int[] -> boolean[]",
            "double[] -> boolean[]",
            "Integer[] -> String[]",
            "List[] -> String[]",
            "Object[] -> String[]",
            "Thread[] -> String[]",
            "Set -> boolean[]", "Set -> int[]", "Set -> double[]", "Set -> String[]", "Set -> Integer[]", "Set -> List[]", "Set -> Object[]", "Set -> Thread[]",
            "Set<String> -> int[]", "Set<String> -> double[]", "Set<String> -> Integer[]", "Set<String> -> List[]", "Set<String> -> Thread[]",
            "Set<Integer> -> List[]", "Set<Integer> -> Thread[]",
            "Set<Object> -> int[]", "Set<Object> -> double[]", "Set<Object> -> Integer[]", "Set<Object> -> List[]", "Set<Object> -> Thread[]",
            "Set<Thread> -> int[]", "Set<Thread> -> double[]", "Set<Thread> -> Integer[]", "Set<Thread> -> List[]"]
  }


  void testParameterToReturn() {
    doTest '''
        @CompileStatic
        %2$s method%3$s(%1$s param) {
          return param
        }
        ''',
           typesXTypes,
           [],
           ['boolean -> int', 'boolean -> double', 'boolean -> short', 'boolean -> byte',
            'boolean[] -> int[]', 'boolean[] -> double[]',
            'int[] -> boolean[]',
            'double[] -> boolean[]',
            'Integer[] -> String[]',
            'List[] -> String[]',
            'Object[] -> String[]',
            'Thread[] -> String[]']
  }

  void testParameterMethodCall() {
    doTest '''
        def bar%3$s(%2$s param) {
        }
        
        @CompileStatic
        def method%3$s(%1$s param) {
          bar%3$s(param)
        }
        ''',
           typesXTypes,
           ['int -> double[]', 'short -> int[]', 'short -> double[]', 'byte -> int[]', 'byte -> double[]', 'short -> Integer[]', 'byte -> Integer[]'],
           ['int[] -> double[]']
  }

  void testReturnAssignValue() {
    doTest '''
        @CompileStatic
        %2$s method%3$s() {
          return %1$s
        }
        ''',
           valuesXTypes,
           [],
           ['true -> int', 'true -> double', 'true -> short', 'true -> byte', 'false -> int', 'false -> double', 'false -> short',
            'false -> byte', '(Void)null -> int', '(Void)null -> double', '(Void)null -> BigDecimal', '(Void)null -> BigInteger',
            '(Void)null -> List', '(Void)null -> Thread', '(Void)null -> List<BigDecimal>', '(Void)null -> List<BigInteger>',
            '(Void)null -> List<Integer>', '(Void)null -> List<String>', '(Void)null -> List<Object>', '(Void)null -> List<Thread>',
            '(Void)null -> boolean[]', '(Void)null -> int[]', '(Void)null -> double[]', '(Void)null -> String[]',
            '(Void)null -> Integer[]', '(Void)null -> List[]', '(Void)null -> Object[]', '(Void)null -> Thread[]',
            '(Void)null -> short', '(Void)null -> byte', '(Void)null -> Set', '(Void)null -> Set<String>',
            '(Void)null -> Set<Integer>', '(Void)null -> Set<Object>', '(Void)null -> Set<Thread>']
  }

  void testLocalAssignValue() {
    doTest '''
        @CompileStatic
        void method%3$s() {
          %2$s param = %1$s
        }
        ''',
           valuesXTypes,
           ['[0L] -> BigInteger', '(Void)null -> Object'],
           [
             '[] -> int', '[] -> double', '[] -> short', '[] -> byte',
             'new ArrayList<>() -> int[]', 'new ArrayList<>() -> double[]', 'new ArrayList<>() -> Integer[]', 'new ArrayList<>() -> List[]', 'new ArrayList<>() -> Thread[]'
           ]
  }

  private void doTest(String body, List<List<String>> arguments, List<String> wrongFalseByIdea, List<String> wrongTrueByIdea) {
    List<String> falseDiff = []
    List<String> trueDiff = []

    Set<String> falseIssues = wrongFalseByIdea as Set<String>
    Set<String> trueIssues = wrongTrueByIdea as Set<String>

    Set<Integer> shellErrors = shellTest(body, arguments)
    Set<Integer> ideaErrors = ideaTest(body, arguments)

    arguments.eachWithIndex { List<String> args, int index ->
      def ideaTest = ideaErrors.contains(index)
      def shellTest = shellErrors.contains(index)
      if (ideaTest != shellTest) {
        def activeIssues = ideaTest ? trueIssues : falseIssues
        def activeDiff = ideaTest ? trueDiff : falseDiff
        def pair = "${args[0]} -> ${args[1]}"
        if (!activeIssues.remove(pair.toString())) {
          activeDiff.add(pair.toString())
        }
      }
    }

    assert falseDiff.isEmpty(), "Idea no error, groovy error : " + falseDiff.collect { "'$it'" }
    assertThat(trueDiff).isEmpty()
    assert falseIssues.isEmpty(), falseIssues.collect { "'$it'" }
    assert trueIssues.isEmpty(), trueIssues.collect { "'$it'" }
  }

  private Set<Integer> ideaTest(String body, List<List<String>> arguments) {
    def text = CS

    arguments.eachWithIndex { List<String> args, int index ->
      text += String.format(body, args[0], args[1], index)
    }

    def offsetLen = CS.readLines().size()
    def lineCount = body.readLines().size() - 1

    def res = new HashSet<Integer>()
    myFixture.with {
      configureByText('_.groovy', text)
      enableInspections(customInspections)
      def highlighting = doHighlighting(HighlightSeverity.ERROR)
      highlighting.each {
        def lineNumber = myFixture.editor.document.getLineNumber(it.startOffset)
        res.add((lineNumber - offsetLen).intdiv(lineCount))
      }
      return res
    }
  }

  private static Set<Integer> shellTest(String body, List<List<String>> arguments) {
    def res = new HashSet<Integer>()
    def shell = new GroovyShell()
    arguments.eachWithIndex { List<String> args, int index ->
      def text = CS + String.format(body, args[0], args[1], index)
      try {
        shell.evaluate(text)
      }
      catch (MultipleCompilationErrorsException ignored) {
        res.add(index)
      }
    }

    return res
  }
}