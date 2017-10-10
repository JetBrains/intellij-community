// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.LightProjectDescriptor
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection

/**
 * Character and char are skipped intentionally.
 * Double and Double[] are skipped intentionally.
 * Current spec https://github.com/apache/groovy/blob/master/src/spec/doc/core-differences-java.adoc#conversions
 * Bug: https://issues.apache.org/jira/browse/GROOVY-7557
 */
class GrAssignAutoTestFalsePositiveTest extends GrHighlightingTestBase {

  List<String> types = ['boolean', 'int', 'double', 'String', 'Integer', 'BigDecimal', 'BigInteger', 'List', 'Object', 'Thread',
                    'List<BigDecimal>', 'List<BigInteger>', 'List<Integer>', 'List<String>', 'List<Object>', 'List<Thread>', 'boolean[]',
                    'int[]', 'double[]', 'String[]', 'Integer[]', 'List[]', 'Object[]', 'Thread[]', 'short', 'byte']

  List<String> values = ['true', '0', '1', '(int)1', '(short)1', '(byte)1', '1.1', '1.1d', '1.1f', '"1"', '["1"]', '1f', '"str"', 'null', 'new Object()',
                     'new Thread()', '[]' /*, '[1]', '[(int)1]', '[(byte)1]'*/, '[1.1]', '[1.1d]', '[1.1f]', '["str"]', 'new ArrayList<>()',
                     '[new Thread()]', '[new Object()]', 'BigInteger.valueOf(1)', 'BigDecimal.valueOf(1.1f)']

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
         'Object[] -> boolean[]', 'Object[] -> String[]', 'Thread[] -> boolean[]', 'Thread[] -> String[]']
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

  void testLocalAssignValue() {
    doTest '''
        import groovy.transform.CompileStatic

        @CompileStatic
        void method() {
          %2$s param = %1$s
        }
        ''',
        vectorProduct(values, types),
        ['[] -> BigInteger'],
        ['["1"] -> int', '["1"] -> double', '["1"] -> boolean[]', '["1"] -> short', '["1"] -> byte', '[] -> int', '[] -> double',
         '[] -> short', '[] -> byte', '[1.1] -> int', '[1.1] -> double', '[1.1] -> Integer', '[1.1] -> boolean[]', '[1.1] -> String[]',
         '[1.1] -> short', '[1.1d] -> double', '[1.1d] -> boolean[]', '[1.1d] -> String[]', '[1.1f] -> double', '[1.1f] -> boolean[]',
         '[1.1f] -> String[]', '["str"] -> int', '["str"] -> double', '["str"] -> boolean[]', '["str"] -> short', '["str"] -> byte',
         '[new Thread()] -> boolean[]', '[new Thread()] -> String[]', '[new Object()] -> boolean[]', '[new Object()] -> String[]']
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
    return vector1.collectMany{arg1 -> vector2.collect{arg2 -> [arg1, arg2]}}
  }

  void doTest(String body, List<List<String>> arguments ,List<String> wrongTrueByIdea, List<String> wrongFalseByIdea) {
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