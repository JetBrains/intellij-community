// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.RepositoryProjectDescriptor
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.util.Slow

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

  List<String> floatValues = ['1.1f', '-1.1f', '+0.002f', '-0F', '034F', '5_132.12F', '0b101101101101f', '0246F', '0xffffF', '1 as Float', '(float) 1.1']
  List<String> doubleValues = ['1.1d', '-1.1d', '+0.002d', '-0D', '034D', '12_345_132.12D', '0b101101101101d', '0246d', '0xffffD', '1 as Double', '(double) 1.1']

  List<String> bigDecimalValues = ['1.1', '-1.2', '+0.002', '-0.0', '034.0G', '12_345_132.12g', '1 as BigDecimal', '(BigDecimal) 1.1']

  List<String> objectValues = ['new Object()', 'new Thread()', '"str"', 'null']
  List<String> listValues = ['[]', '[1]', '[0L]', '[1.1]', '[1.2f]', '["str"]', 'new ArrayList<>()', '[new Object()]', '[new Thread()]']

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

  @Override
  @NotNull
  protected LightProjectDescriptor getProjectDescriptor() {
    return new RepositoryProjectDescriptor("org.codehaus.groovy:groovy:2.4.15") {
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
           vectorProduct(types, types),
           [],
           ['boolean -> int', 'boolean -> double', 'boolean -> short', 'boolean -> byte',
            'boolean[] -> int[]', 'boolean[] -> double[]', 'boolean[] -> String[]',
            'int[] -> boolean[]', 'int[] -> String[]',
            'double[] -> boolean[]', 'double[] -> String[]',
            'String[] -> boolean[]',
            'Integer[] -> boolean[]', 'Integer[] -> String[]',
            'List[] -> boolean[]', 'List[] -> String[]',
            'Object[] -> boolean[]', 'Object[] -> String[]',
            'Thread[] -> boolean[]', 'Thread[] -> String[]']
  }


  void testParameterToReturn() {
    doTest '''
        @CompileStatic
        %2$s method%3$s(%1$s param) {
          return param
        }
        ''',
           vectorProduct(types, types),
           [],
           ['boolean -> int', 'boolean -> double', 'boolean -> short', 'boolean -> byte',
            'boolean[] -> int[]', 'boolean[] -> double[]', 'boolean[] -> String[]',
            'int[] -> boolean[]', 'int[] -> String[]',
            'double[] -> boolean[]', 'double[] -> String[]',
            'String[] -> boolean[]',
            'Integer[] -> boolean[]', 'Integer[] -> String[]',
            'List[] -> boolean[]', 'List[] -> String[]',
            'Object[] -> boolean[]', 'Object[] -> String[]',
            'Thread[] -> boolean[]', 'Thread[] -> String[]']
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
           vectorProduct(types, types),
           ['int -> double[]', 'short -> int[]', 'short -> double[]', 'byte -> int[]', 'byte -> double[]'],
           ['Integer[] -> int[]', 'Integer[] -> double[]', 'int[] -> double[]', 'int[] -> Integer[]']
  }

  void testReturnAssignValue() {
    doTest '''
        @CompileStatic
        %2$s method%3$s() {
          return %1$s
        }
        ''',
           vectorProduct(values, types),
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
           vectorProduct(values, types),
           ['[] -> BigInteger', '[1] -> BigInteger'],
           ['[] -> int', '[] -> double', '[] -> short', '[] -> byte', '[] -> int',
            '[1.1] -> boolean[]', '[1.1] -> String[]',
            '[1] -> int', '[1] -> double', '[1] -> int', '[1] -> boolean[]', '[1] -> String[]',
            '[0L] -> double', '[0L] -> boolean[]', '[0L] -> String[]',
            '[1.2f] -> double', '[1.2f] -> boolean[]', '[1.2f] -> String[]',
            '["str"] -> int', '["str"] -> double', '["str"] -> boolean[]', '["str"] -> short', '["str"] -> byte',
            '[new Object()] -> boolean[]', '[new Object()] -> String[]',
            '[new Thread()] -> boolean[]', '[new Thread()] -> String[]',
            'new ArrayList<>() -> boolean[]', 'new ArrayList<>() -> int[]', 'new ArrayList<>() -> double[]', 'new ArrayList<>() -> String[]', 'new ArrayList<>() -> Integer[]', 'new ArrayList<>() -> List[]', 'new ArrayList<>() -> Object[]', 'new ArrayList<>() -> Thread[]']
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

    Set<String> trueIssues = wrongTrueByIdea as Set<String>
    Set<String> falseIssues = wrongFalseByIdea as Set<String>

    Set<Integer> shellErrors = shellTest(body, arguments)
    Set<Integer> ideaErrors = ideaTest(body, arguments)

    arguments.eachWithIndex { List<String> args, int index ->
      def ideaTest = !ideaErrors.contains(index)
      def shellTest = !shellErrors.contains(index)
      if (ideaTest != shellTest) {
        def activeIssues = ideaTest ? trueIssues : falseIssues
        def activeDiff = ideaTest ? trueDiff : falseDiff
        def pair = "${args[0]} -> ${args[1]}"
        if (!activeIssues.remove(pair.toString())) {
          activeDiff.add(pair.toString())
        }
      }
    }

    assert falseDiff.isEmpty(), "Idea false, groovy true : " + falseDiff.collect { "'$it'" }
    assert trueDiff.isEmpty(), "Idea true, groovy false : " + trueDiff.collect { "'$it'" }
    assert falseIssues.isEmpty(), falseIssues.collect { "'$it'" }
    assert trueIssues.isEmpty(), trueIssues.collect { "'$it'" }
  }

  Set<Integer> ideaTest(String body, List<List<String>> arguments) {
    def text = CS

    arguments.eachWithIndex { List<String> args, int index ->
      text += String.format(body, args[0], args[1], index)
    }

    def offsetLen = CS.readLines().size()
    def lineCount = body.readLines().size() - 1

    def res = new HashSet()
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

  static Set<Integer> shellTest(String body, List<List<String>> arguments) {
    def res = new HashSet()
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