// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.spock;

import com.intellij.groovy.testFramework.TypingTest;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.declaration.GrMethodMayBeStaticInspection;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class SpockTest implements TypingTest, HighlightingTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(SpockTestUtil.SPOCK_PROJECT);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = lightJavaCodeInsightFixture(project);

  @BeforeEach
  void setUpTestDataPath() {
    groovy.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath());
  }

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @Test
  public void testCompletion() {
    configureByText(
      """
        class FooSpec extends spock.lang.Specification {
          def "foo test"() {
            expect:
            <caret>
        
            where:
            varAssigment = "asdad"
            varShl << ['aaa', 'bbb']
            [varShl1, varShl2, varShl3] << [['aaa', 'bbb', 'ccc'], ['aaa', 'bbb', 'ccc'], ['aaa', 'bbb', 'ccc']]
        
            varTable1|varTable2|varTable3||varTable4
            ""|""|""||""
          }
        }
        """);
    runInEdtAndWait(() -> {
      getFixture().completeBasic();
      getFixture().assertPreferredCompletionItems(
        0,
        "varAssigment", "varShl", "varShl1", "varShl2", "varShl3", "varTable1", "varTable2", "varTable3", "varTable4"
      );
    });
  }

  @Test
  public void testEquals() {
    typingTest(
      """
        class FooSpec extends spock.lang.Specification {
          def "foo test"() {
            expect:
            <caret>name
        
            where:
            name = "xxx"
          }
        }
        """, JAVA_LANG_STRING);
  }

  @Test
  public void testTable() {
    typingTest(
      """
        class FooSpec extends spock.lang.Specification {
          def "foo test"() {
            List<Byte> list = zzz()
            expect:
            <caret>name
        
            where:
            varTable1|varT.a.ble2|varTable3|name
            ""|""|""|list
            ""|""|""|[1,2L,3d]
            ""|""|""|null
          }
        }
        """, "java.util.List<? extends java.lang.Number>");
  }

  @Test
  public void testShlSimple() {
    typingTest(
      """
        class FooSpec extends spock.lang.Specification {
          def "foo test"() {
            expect:
            <caret>name
        
            where:
            name << [new HashMap(), new TreeMap(), [aaa:1, bbb:2], null]
          }
        }
        """, "[java.util.AbstractMap,java.lang.Cloneable,java.io.Serializable]");
  }

  @Test
  public void testShlMulti1() {
    typingTest(
      """
        class FooSpec extends spock.lang.Specification {
          def "foo test"() {
            def c = { return "1111" }
        
            expect:
            <caret>name
        
            where:
            [x1, _, name] << [['x', 'y', c()], ['x', 'y', null]]
          }
        }
        """, JAVA_LANG_STRING);
  }

  @Test
  public void testShlMulti2() {
    typingTest(
      """
        class FooSpec extends spock.lang.Specification {
          def "foo test"() {
            def list = ["a", "b", "c"]
        
            expect:
            <caret>name
        
            where:
            [x1, _, name] << [list, ['aaa', 'bbb', 'ccc']]
          }
        }
        """, JAVA_LANG_STRING);
  }

  @Test
  public void testRename() {
    getFixture().configureByText("FooSpec.groovy",
                                 """
                                   class FooSpec extends spock.lang.Specification {
                                     @spock.lang.Unroll("xxx #name a #name #name    #name")
                                     def "foo test"() {
                                       expect:
                                       name == null || name.length() == 1
                                   
                                       where:
                                       [x1, _, name<caret>] << [['x', 'y', 'x'], ['x', 'y', null]]
                                     }
                                   }
                                   """);

    runInEdtAndWait(() -> getFixture().renameElementAtCaret("n"));

    getFixture().checkResult(
      """
        class FooSpec extends spock.lang.Specification {
          @spock.lang.Unroll("xxx #n a #n #n    #n")
          def "foo test"() {
            expect:
            n == null || n.length() == 1
        
            where:
            [x1, _, n] << [['x', 'y', 'x'], ['x', 'y', null]]
          }
        }
        """);

    runInEdtAndWait(() -> getFixture().renameElementAtCaret("z1234567890"));

    getFixture().checkResult(
      """
        class FooSpec extends spock.lang.Specification {
          @spock.lang.Unroll("xxx #z1234567890 a #z1234567890 #z1234567890    #z1234567890")
          def "foo test"() {
            expect:
            z1234567890 == null || z1234567890.length() == 1
        
            where:
            [x1, _, z1234567890] << [['x', 'y', 'x'], ['x', 'y', null]]
          }
        }
        """);
  }

  @Test
  public void testVariable_NotExistingInCompletion() {
    getFixture().configureByText("FooSpec.groovy",
                                 """
                                   class FooSpec extends spock.lang.Specification {
                                     def "foo test"() {
                                       String subscriber = Mock()
                                       then: (0.._) * subscriber.concat(<caret>)
                                     }
                                   }
                                   """);
    List<String> elements = EdtTestUtil.runInEdtAndGet(() -> {
      getFixture().completeBasic();
      return getFixture().getLookupElementStrings();
    });
    assertTrue(elements.contains("_"));
  }

  @Disabled("see com.intellij.execution.junit2.inspection.JUnitCantBeStaticExtension")
  @Test
  public void testMethodMayBeStatic() {
    GrMethodMayBeStaticInspection inspection = new GrMethodMayBeStaticInspection();
    inspection.myIgnoreEmptyMethods = false;
    highlightingTest(
      """
        class SomeSpec extends spock.lang.Specification {
          def cleanup() {}
          def setupSpec() {}
          def <warning descr="Method may be static">regularMethod</warning>() {}
          def featureMethod() {
            expect: 1 == 1
          }
        }
        """, inspection);
  }
}
