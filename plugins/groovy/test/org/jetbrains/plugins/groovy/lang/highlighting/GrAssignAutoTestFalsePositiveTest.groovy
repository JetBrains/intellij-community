/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
 * Double and Double[] is skipped intentionally.
 * Current spec https://github.com/apache/groovy/blob/master/src/spec/doc/core-differences-java.adoc#conversions
 * Bug: https://issues.apache.org/jira/browse/GROOVY-7557
 */
class GrAssignAutoTestFalsePositiveTest extends GrHighlightingTestBase {

  String[] types = ['boolean', 'int', 'double', 'String', 'Integer', 'BigDecimal', 'BigInteger', 'List', 'Object', 'Thread',
                    'List<BigDecimal>', 'List<BigInteger>', 'List<Integer>', 'List<String>', 'List<Object>', 'List<Thread>', 'boolean[]',
                    'int[]', 'double[]', 'String[]', 'Integer[]', 'List[]', 'Object[]', 'Thread[]']

  String[] values = ['true', '0', '1', '(int)1', '1.1', '1.1d', '1.1f', '"1"', '["1"]', '1f', '"str"', 'null', 'new Object()',
                     'new Thread()' /*'[]' , '[1]', '[(int)1]', '[(byte)1]'*/, '[1.1]', '[1.1d]', '[1.1f]', '["str"]', 'new ArrayList<>()',
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
        ''', 'Parameter: %1$s, local var: %2$s. Groovy - %3$s , Idea - %4$s\n'
  }


  void testParameterToReturn() {
    doTest '''
        import groovy.transform.CompileStatic

        @CompileStatic
        %2$s method(%1$s param) {
          return param
        }
        ''', 'Parameter: %1$s, return value: %2$s. Groovy - %3$s , Idea - %4$s\n'
  }

  void testLocalAssignValue() {
    doValueTest '''
        import groovy.transform.CompileStatic

        @CompileStatic
        void method() {
          %1$s param = %2$s
        }
        ''', 'Parameter: %1$s, value: %2$s. Groovy - %3$s , Idea - %4$s\n'
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

  void testEmptyListAssign() {
    testHighlighting '''
    import groovy.transform.CompileStatic

    @CompileStatic
    void method() {
      Integer param = []
    }
'''
  }

  void doTest(String body, String errorText) {
    List<String> diff = []
    for (String type1 : types) {
      for (String type2 : types) {
        String text = String.format(body, type1, type2)
        boolean shellTest = shellTest(text)
        boolean ideaTest = ideaTest(text)
        if (!shellTest && ideaTest) {
          diff.add(String.format(errorText, type1, type2, shellTest, ideaTest))
        }
      }
    }

    assert diff.isEmpty(), (diff.size().toString() + " " + diff)
  }

  void doValueTest(String body, String errorText) {
    List<String> diff = []
    for (String type : types) {
      for (String value : values) {
        String text = String.format(body, type, value)
        boolean shellTest = shellTest(text)
        boolean ideaTest = ideaTest(text)
        if (!shellTest && ideaTest) {
          diff.add(String.format(errorText, type, value, shellTest, ideaTest))
        }
      }
    }

    assert diff.isEmpty(), (diff.size().toString() + " " + diff)
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