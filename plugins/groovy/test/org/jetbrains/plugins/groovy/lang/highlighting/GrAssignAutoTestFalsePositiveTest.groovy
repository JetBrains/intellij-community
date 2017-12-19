/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.LightProjectDescriptor
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.util.Slow

/**
 * Character and char are skipped intentionally.
 * Double and Double[] are skipped intentionally.
 * Current spec https://github.com/apache/groovy/blob/master/src/spec/doc/core-differences-java.adoc#conversions
 * Bug: https://issues.apache.org/jira/browse/GROOVY-7557
 */
@Slow
class GrAssignAutoTestFalsePositiveTest extends GrHighlightingTestBase {

  List<String> types = ['boolean', 'int', 'double', 'String', 'BigDecimal', 'BigInteger', 'List', 'Object', 'Thread',
                        'List<BigDecimal>', 'List<BigInteger>', 'List<Integer>', 'List<String>', 'List<Object>', 'List<Thread>', 'boolean[]',
                        'int[]', 'double[]', 'String[]', 'Integer[]', 'List[]', 'Object[]', 'Thread[]', 'short', 'byte', 'Set', 'Set<String>',
                        'Set<Integer>', 'Set<Object>', 'Set<Thread>']


  List<String> booleanValues = ['true', 'false', '(Boolean)true', 'false as Boolean']
  List<String> byteValues = ['(byte) 0', '-1 as Byte', '(byte)(+2)', '126', '-127']
  List<String> shortValues = ['(short) 0', '-1 as Short', '(Short)(+2)', '-32768', '32767']
  List<String> intValues = ['0', '-1', '+1', '(Integer)(+2)', '1i', '-1I', '+1 as int', '+32768', '2E3', '-2E4', '0b101101101101', '0246', '0xffff', '-0x77', '1234_5678']
  List<String> longValues = ['0l', '-1L', '+2L', '(Long)(+2)', '+1 as long', '1234_5678_9012_3456L', '0b101101101101L', '0246L', '0xffffL', '-0x77L', '0x7fff_ffff_ffff_ffffL', 'new Long("123")']
  List<String> bigIntegerValues = ['BigInteger.valueOf(1)', '-1 as BigInteger', '+2G', '-0G', '034G', '1234_5678_9012_3456G', '0b101101101101G', '0246G', '0xffffG', '-0x77G']

  List<String> floatValues = ['1.1f', '-1.1f', '+0.002f', '-0F', '034F', '5_132.12F', '0b101101101101f', '0246F', '0xffffF', '0x1.0p0F', '1 as Float', '(float) 1.1']
  List<String> doubleValues = ['1.1d', '-1.1d', '+0.002d', '-0D', '034D', '12_345_132.12D', '0b101101101101d', '0246d', '0xffffD', '0x1.0p0D', '1 as Double', '(double) 1.1']

  List<String> bigDecimalValues = ['1.1', '-1.2', '+0.002', '-0.0', '034.0G', '12_345_132.12g', '0x1.0p0', '1 as BigDecimal', '(BigDecimal) 1.1']

  List<String> objectValues = ['new Object()', 'new Thread()', '"str"', 'null']
  List<String> listValues = ['[]', '[1]', '[0L]', '[1.1]', '[1.2f]', '["str]', 'new ArrayList<>()', '[new Object()], [new Thread()]']

  List<String> voidValues = ['print("")', '(Void)null']

  List<String> values = booleanValues +
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

  def shell = GroovyShell.newInstance()

  @Override
  @NotNull
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyLightProjectDescriptor.GROOVY_LATEST_REAL_JDK
  }

  @Override
  InspectionProfileEntry[] getCustomInspections() { [new GroovyAssignabilityCheckInspection()] }

  void testParameterToLocal() {
    doTest '''
        import groovy.transform.CompileStatic

        @CompileStatic
        void method(%1$s param) {
          %2$s local = param
        }
        ''',
           vectorProduct(types, types),
           [],
           ['boolean -> int', 'boolean -> double', 'boolean -> short', 'boolean -> byte', 'List<BigDecimal> -> boolean[]',
            'List<BigDecimal> -> int[]', 'List<BigDecimal> -> String[]', 'List<BigDecimal> -> Integer[]', 'List<BigDecimal> -> List[]',
            'List<BigDecimal> -> Thread[]', 'List<BigInteger> -> boolean[]', 'List<BigInteger> -> int[]', 'List<BigInteger> -> double[]',
            'List<BigInteger> -> String[]', 'List<BigInteger> -> Integer[]', 'List<BigInteger> -> List[]', 'List<BigInteger> -> Thread[]',
            'List<Integer> -> boolean[]', 'List<Integer> -> String[]', 'List<Integer> -> List[]', 'List<Integer> -> Thread[]',
            'List<String> -> boolean[]', 'List<String> -> int[]', 'List<String> -> double[]', 'List<String> -> Integer[]',
            'List<String> -> List[]', 'List<String> -> Thread[]', 'List<Object> -> boolean[]', 'List<Object> -> int[]',
            'List<Object> -> double[]', 'List<Object> -> String[]', 'List<Object> -> Integer[]', 'List<Object> -> List[]',
            'List<Object> -> Thread[]', 'List<Thread> -> boolean[]', 'List<Thread> -> int[]', 'List<Thread> -> double[]',
            'List<Thread> -> String[]', 'List<Thread> -> Integer[]', 'List<Thread> -> List[]', 'boolean[] -> int[]', 'boolean[] -> double[]',
            'boolean[] -> String[]', 'int[] -> boolean[]', 'int[] -> String[]', 'double[] -> boolean[]', 'double[] -> String[]',
            'String[] -> boolean[]', 'Integer[] -> boolean[]', 'Integer[] -> String[]', 'List[] -> boolean[]', 'List[] -> String[]',
            'Object[] -> boolean[]', 'Object[] -> String[]', 'Thread[] -> boolean[]', 'Thread[] -> String[]', 'Set<String> -> boolean[]',
            'Set<String> -> int[]', 'Set<String> -> double[]', 'Set<String> -> Integer[]', 'Set<String> -> List[]', 'Set<String> -> Thread[]',
            'Set<Integer> -> boolean[]', 'Set<Integer> -> String[]', 'Set<Integer> -> List[]', 'Set<Integer> -> Thread[]',
            'Set<Object> -> boolean[]', 'Set<Object> -> int[]', 'Set<Object> -> double[]', 'Set<Object> -> String[]',
            'Set<Object> -> Integer[]', 'Set<Object> -> List[]', 'Set<Object> -> Thread[]', 'Set<Thread> -> boolean[]',
            'Set<Thread> -> int[]', 'Set<Thread> -> double[]', 'Set<Thread> -> String[]', 'Set<Thread> -> Integer[]', 'Set<Thread> -> List[]',
            'boolean -> int']
  }


  void testParameterToReturn() {
    doTest '''
        import groovy.transform.CompileStatic

        @CompileStatic
        %2$s method(%1$s param) {
          return param
        }
        ''',
           vectorProduct(types, types),
           [],
           ['boolean -> int', 'boolean -> double', 'boolean -> short', 'boolean -> byte', 'boolean[] -> int[]', 'boolean[] -> double[]',
            'boolean[] -> String[]', 'int[] -> boolean[]', 'int[] -> String[]', 'double[] -> boolean[]', 'double[] -> String[]',
            'String[] -> boolean[]', 'Integer[] -> boolean[]', 'Integer[] -> String[]', 'List[] -> boolean[]', 'List[] -> String[]',
            'Object[] -> boolean[]', 'Object[] -> String[]', 'Thread[] -> boolean[]', 'Thread[] -> String[]']
  }

  void testParameterMethodCall() {
    doTest '''
        import groovy.transform.CompileStatic

        def bar(%2$s param) {
        }
        
        @CompileStatic
        def method(%1$s param) {
          bar(param)
        }
        ''',
           vectorProduct(types, types),
           ['int -> double[]', 'short -> int[]', 'short -> double[]', 'short -> Integer[]', 'byte -> int[]', 'byte -> double[]',
            'byte -> Integer[]'],
           ['Integer[] -> int[]', 'Integer[] -> double[]',
            'BigDecimal -> int', 'BigDecimal -> int[]', 'BigDecimal -> short', 'BigInteger -> int', 'BigInteger -> double',
            'BigInteger -> int[]', 'BigInteger -> double[]', 'BigInteger -> short', 'int[] -> double[]', 'int[] -> Integer[]']
  }

  void testLocalAssignValue() {
    doTest '''
        import groovy.transform.CompileStatic

        @CompileStatic
        void method() {
          %2$s param = %1$s
        }
        ''',
           vectorProduct(values, types),
           ['[] -> BigInteger', '[1] -> BigInteger', '[1.1] -> BigDecimal'],
           ['[] -> int', '[] -> double', '[] -> short', '[] -> byte', '[1.1] -> int', '[1.1] -> double', '[1.1] -> boolean[]',
            '[1.1] -> String[]', '[1.1] -> short', '[] -> int', '[1] -> int', '[1] -> double', '[1] -> int',
            '[1] -> boolean[]', '[1] -> String[]', '[0L] -> double', '[0L] -> boolean[]', '[0L] -> String[]', '[1.1] -> int',
            '[1.2f] -> double', '[1.2f] -> boolean[]', '[1.2f] -> String[]', '[] -> int', '[1] -> int', '[1.1] -> int']
  }

  void testWrongConstructorResolve() {
    testHighlighting '''
    import groovy.transform.CompileStatic

    @CompileStatic
    void method() {
      BigInteger param = [(int)1]
    }
'''
  }

  static List<List<String>> vectorProduct(List<String> vector1, List<String> vector2) {
    return vector1.collectMany { arg1 -> vector2.collect { arg2 -> [arg1, arg2] } }
  }

  void doTest(String body, List<List<String>> arguments, List<String> wrongTrueByIdea, List<String> wrongFalseByIdea) {
    List<String> falseDiff = []
    List<String> trueDiff = []
    Set<String> trueIssues = wrongTrueByIdea
    Set<String> falseIssues = wrongFalseByIdea
    for (List<String> args : arguments) {
      String pair = "${args[0]} -> ${args[1]}"
      String text = String.format(body, args[0], args[1])
      boolean shellTest = shellTest(text)
      boolean ideaTest = ideaTest(text)
      if (shellTest != ideaTest) {
        def activeIssues = ideaTest ? trueIssues : falseIssues
        def activeDiff = ideaTest ? trueDiff : falseDiff
        if (!activeIssues.remove(pair)) {
          activeDiff.add(pair)
        }
      }
    }

    assert falseDiff.isEmpty(), "Idea false, groovy true : " + falseDiff.collect { "'$it'" }
    assert trueDiff.isEmpty(), "Idea true, groovy false : " + trueDiff.collect { "'$it'" }
    assert falseIssues.isEmpty(), falseIssues.collect { "'$it'" }
    assert trueIssues.isEmpty(), trueIssues.collect { "'$it'" }
  }

  boolean ideaTest(String body) {
    myFixture.with {
      configureByText('_.groovy', body)
      enableInspections(customInspections)
      def highlighting = doHighlighting(HighlightSeverity.ERROR)
      return highlighting.size() == 0
    }
  }

  boolean shellTest(String scriptText) {
    try {
      shell.evaluate(scriptText)
    }
    catch (MultipleCompilationErrorsException ignored) {
      return false
    }
    return true
  }
}