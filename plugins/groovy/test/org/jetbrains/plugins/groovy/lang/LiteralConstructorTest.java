// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.groovy.testFramework.ResolveTest;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyConstructorReference;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyConstructorResult;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyPropertyWriteReference;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@TestApplication
public class LiteralConstructorTest implements HighlightingTest, ResolveTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5_REAL_JDK);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @BeforeEach
  public void setUp() {
    runInEdtAndWait(() -> {
      RecursionManager.assertOnRecursionPrevention(getFixture().getTestRootDisposable());
      getFixture().addFileToProject("classes.groovy", """
        enum En { ONE, TWO, }
        class Person {
          String name
          int age
          Person parent
        }
        class C {
          C() {}
          C(Object a, String b) {}
          C(String a, Object b) {}
        }
        class NoDefaultConstructor {
          NoDefaultConstructor(int a) {}
        }
        class CollectionConstructor {
          CollectionConstructor(Collection c) {}
          CollectionConstructor(Object a, String b) {}
          CollectionConstructor(String a, Object b) {}
        }
        class M {
          M() {}
          M(Map m) {}
        }
        class CollectionNoArgConstructor implements Collection {
          CollectionNoArgConstructor() {}
        }
        """);
    });
  }

  @Test
  public void constructorReference() {
    runInEdtAndWait(() -> {
      LinkedHashMap<String, Boolean> data = new LinkedHashMap<>();
      data.put("boolean boo = [<caret>]", false);
      data.put("boolean boo = [<caret>:]", false);
      data.put("byte b = [<caret>]", false);
      data.put("byte b = [<caret>:]", false);
      data.put("char c = [<caret>]", false);
      data.put("char c = [<caret>:]", false);
      data.put("double d = [<caret>]", false);
      data.put("double d = [<caret>:]", false);
      data.put("float f = [<caret>]", false);
      data.put("float f = [<caret>:]", false);
      data.put("int i = [<caret>]", false);
      data.put("int i = [<caret>:]", false);
      data.put("long l = [<caret>]", false);
      data.put("long l = [<caret>:]", false);
      data.put("short s = [<caret>]", false);
      data.put("short s = [<caret>:]", false);
      data.put("void v = [<caret>]", false);
      data.put("void v = [<caret>:]", false);
      data.put("Boolean boo = [<caret>]", false);
      data.put("Boolean boo = [<caret>:]", false);
      data.put("Byte b = [<caret>]", false);
      data.put("Byte b = [<caret>:]", false);
      data.put("Character c = [<caret>]", false);
      data.put("Character c = [<caret>:]", false);
      data.put("Double d = [<caret>]", false);
      data.put("Double d = [<caret>:]", false);
      data.put("Float f = [<caret>]", false);
      data.put("Float f = [<caret>:]", false);
      data.put("Integer i = [<caret>]", false);
      data.put("Integer i = [<caret>:]", false);
      data.put("Long l = [<caret>]", false);
      data.put("Long l = [<caret>:]", false);
      data.put("Short s = [<caret>]", false);
      data.put("Short s = [<caret>:]", false);
      data.put("Void v = [<caret>]", true);
      data.put("Void v = [<caret>:]", true);
      data.put("def a = [<caret>]", false);
      data.put("def a = [<caret>:]", false);
      data.put("Object a = [<caret>]", false);
      data.put("Object a = [<caret>:]", false);
      data.put("Class a = [<caret>]", false);
      data.put("Class a = [<caret>:]", false);
      data.put("String s = [<caret>]", false);
      data.put("String s = [<caret>:]", false);
      data.put("En e = [<caret>]", false);
      data.put("En e = [<caret>:]", false);
      data.put("Collection c = [<caret>]", false);
      data.put("Collection c = [<caret>:]", true);
      data.put("List l = [<caret>]", false);
      data.put("List l = [<caret>:]", true);
      data.put("ArrayList c = [<caret>]", false);
      data.put("ArrayList c = [<caret>:]", true);
      data.put("Map m = [<caret>]", true);
      data.put("Map m = [<caret>:]", false);
      data.put("HashMap hm = [<caret>]", true);
      data.put("HashMap hm = [<caret>:]", false);
      data.put("LinkedHashMap lhm = [<caret>]", true);
      data.put("LinkedHashMap lhm = [<caret>:]", false);
      data.put("Set s = [<caret>]", false);
      data.put("AbstractSet aS = [<caret>]", false);
      data.put("HashSet hs = [<caret>]", true);
      data.put("LinkedHashSet lhs = [<caret>]", false);
      data.put("Set s = [<caret>:]", true);
      data.put("HashSet hs = [<caret>:]", true);
      data.put("LinkedHashSet lhs = [<caret>:]", true);
      data.put("Person p = [<caret>]", true);
      data.put("Person p = [<caret>:]", true);
      constructorReferenceTest(data);
    });
  }

  @Test
  public void constructorReferenceCS() {
    runInEdtAndWait(() -> {
      LinkedHashMap<String, Boolean> data = new LinkedHashMap<>();
      data.put("boolean boo = [<caret>]", false);
      data.put("byte b = [<caret>]", false);
      data.put("char c = [<caret>]", false);
      data.put("double d = [<caret>]", false);
      data.put("float f = [<caret>]", false);
      data.put("int i = [<caret>]", false);
      data.put("long l = [<caret>]", false);
      data.put("short s = [<caret>]", false);
      data.put("void v = [<caret>]", false);
      data.put("boolean boo = [<caret>1]", false);
      data.put("byte b = [<caret>1]", true);
      data.put("char c = [<caret>1]", true);
      data.put("double d = [<caret>1]", true);
      data.put("float f = [<caret>1]", true);
      data.put("int i = [<caret>1]", true);
      data.put("long l = [<caret>1]", true);
      data.put("short s = [<caret>1]", true);
      data.put("void v = [<caret>1]", true);
      data.put("boolean boo = [<caret>:]", false);
      data.put("byte b = [<caret>:]", true);
      data.put("char c = [<caret>:]", true);
      data.put("double d = [<caret>:]", true);
      data.put("float f = [<caret>:]", true);
      data.put("int i = [<caret>:]", true);
      data.put("long l = [<caret>:]", true);
      data.put("short s = [<caret>:]", true);
      data.put("void v = [<caret>:]", true);
      data.put("Boolean boo = [<caret>]", false);
      data.put("Byte b = [<caret>]", true);
      data.put("Character c = [<caret>]", true);
      data.put("Double d = [<caret>]", true);
      data.put("Float f = [<caret>]", true);
      data.put("Integer i = [<caret>]", true);
      data.put("Long l = [<caret>]", true);
      data.put("Short s = [<caret>]", true);
      data.put("Void v = [<caret>]", true);
      data.put("Boolean boo = [<caret>:]", false);
      data.put("Byte b = [<caret>:]", true);
      data.put("Character c = [<caret>:]", true);
      data.put("Double d = [<caret>:]", true);
      data.put("Float f = [<caret>:]", true);
      data.put("Integer i = [<caret>:]", true);
      data.put("Long l = [<caret>:]", true);
      data.put("Short s = [<caret>:]", true);
      data.put("Void v = [<caret>:]", true);
      data.put("BigInteger bi = [<caret>]", true);
      data.put("BigInteger bi = [<caret>1]", true);
      data.put("BigInteger bi = [<caret>:]", true);
      data.put("def a = [<caret>]", false);
      data.put("def a = [<caret>:]", false);
      data.put("Object a = [<caret>]", false);
      data.put("Object a = [<caret>:]", false);
      data.put("Class a = [<caret>]", false);
      data.put("Class a = [<caret>:]", false);
      data.put("String s = [<caret>]", false);
      data.put("String s = [<caret>:]", false);
      data.put("Set s = [<caret>]", false);
      data.put("Set s = [<caret>:]", true);
      data.put("Person p = [<caret>]", true);
      data.put("Person p = [<caret>:]", true);
      constructorReferenceTest(data, true);
    });
  }

  @Test
  public void constructorReferenceFromNamedArgument() {
    runInEdtAndWait(() -> {
      LinkedHashMap<String, Boolean> data = new LinkedHashMap<>(5);
      data.put("Person p = [parent: [<caret>]]", true);
      data.put("Person p = [parent: [<caret>:]]", true);
      data.put("Person p = new Person(parent: [<caret>]])", true);
      data.put("Person p = new Person(parent: [<caret>:])", true);
      data.put("M m = [abc:[<caret>:]]", false);
      constructorReferenceTest(data);
    });
  }

  @Test
  public void constructorReferenceFromSafeCast() {
    runInEdtAndWait(() -> {
      LinkedHashMap<String, Boolean> data = new LinkedHashMap<>(12);
      data.put("[<caret>] as List", false);
      data.put("[<caret>] as Set", false);
      data.put("[<caret>] as SortedSet", false);
      data.put("[<caret>] as Queue", false);
      data.put("[<caret>] as Stack", false);
      data.put("[<caret>] as LinkedList", false);
      data.put("[<caret>] as CollectionConstructor", false);
      data.put("[<caret>] as CollectionNoArgConstructor", false);
      data.put("[<caret>] as String", false);
      data.put("[<caret>:] as List", false);
      data.put("[<caret>] as Person", true);
      data.put("[<caret>:] as Person", true);
      constructorReferenceTest(data);
    });
  }

  private void constructorReferenceTest(LinkedHashMap<String, Boolean> data, boolean cs) {
    TestUtils.runAll(data, (text, expected) -> {
      String fullText = cs ? "@groovy.transform.CompileStatic def cs() { " + text + " }" : text;
      GrListOrMap listOrMap = elementUnderCaret(fullText, GrListOrMap.class);
      GroovyConstructorReference reference = listOrMap.getConstructorReference();
      if (expected) {
        Assertions.assertNotNull(reference, text);
      }
      else {
        Assertions.assertNull(reference, text);
      }
    });
  }

  private void constructorReferenceTest(LinkedHashMap<String, Boolean> data) {
    constructorReferenceTest(data, false);
  }

  @Test
  public void propertyReference() {
    runInEdtAndWait(() -> {
      LinkedHashMap<String, Boolean> data = new LinkedHashMap<>(8);
      data.put("Person p = [<caret>parent: [parent: []]]", true);
      data.put("Person p = [parent: [<caret>parent: []]]", true);
      data.put("Person p = new Person(<caret>parent: [parent: []])", true);
      data.put("Person p = new Person(parent: [<caret>parent: []])", true);
      data.put("C c = [<caret>a: [a: []]]", true);
      data.put("C c = [a: [<caret>a: []]]", false);
      data.put("M m = [<caret>abc: []]", false);
      data.put("foo(<caret>abc: 1)", false);
      TestUtils.runAll(data, (text, expected) -> {
        GrArgumentLabel label = elementUnderCaret(text, GrArgumentLabel.class);
        GroovyPropertyWriteReference reference = label.getConstructorPropertyReference();
        if (expected) {
          Assertions.assertNotNull(reference, text);
        }
        else {
          Assertions.assertNull(reference, text);
        }
      });
    });
  }

  @Test
  public void resolveConstructorApplicable() {
    runInEdtAndWait(() -> {
      LinkedHashMap<String, List<Serializable>> data = new LinkedHashMap<>();
      data.put("Person p = <caret>[]", List.of(0, false));
      data.put("Person p = <caret>[:]", List.of(0, true));
      data.put("C c = <caret>[]", List.of(0, false));
      data.put("C c = <caret>[1, \"42\"]", List.of(2, false));
      data.put("C c = <caret>[:]", List.of(0, true));
      data.put("C c = <caret>[a: 1]", List.of(0, true));
      data.put("M m = <caret>[]", List.of(0, false));
      data.put("M m = <caret>[:]", List.of(1, false));
      data.put("M m = <caret>[a: 1]", List.of(1, false));
      TestUtils.runAll(data, (text, expected) -> {
        int parametersCount = (int)expected.get(0);
        boolean mapConstructor = (boolean)expected.get(1);
        GroovyConstructorResult result = assertInstanceOf(GroovyConstructorResult.class, advancedResolveByText(text));
        Assertions.assertEquals(mapConstructor, result.isMapConstructor(), text);
        Assertions.assertEquals(Applicability.applicable, result.getApplicability(), text);
        PsiMethod method = result.getElement();
        Assertions.assertTrue(method.isConstructor(), text);
        Assertions.assertEquals(parametersCount, method.getParameterList().getParametersCount(), text);
      });
    });
  }

  @Test
  public void resolveConstructorInapplicable() {
    runInEdtAndWait(() -> {
      LinkedHashMap<String, Integer> data = new LinkedHashMap<>(3);
      data.put("C c = <caret>[1]", 3);
      data.put("NoDefaultConstructor ndc = <caret>[]", 1);
      data.put("NoDefaultConstructor ndc = <caret>[:]", 1);
      TestUtils.runAll(data, (text, resultsCount) -> {
        Collection<? extends GroovyResolveResult> results = multiResolveByText(text);
        Assertions.assertEquals((int)resultsCount, results.size());
        for (GroovyResolveResult result : results) {
          Assertions.assertEquals(Applicability.inapplicable, ((GroovyMethodResult)result).getApplicability());
        }
      });
    });
  }

  @Test
  public void resolveListLiteralAmbiguous() {
    runInEdtAndWait(() -> {
      Collection<? extends GroovyResolveResult> results = multiResolveByText("C c = <caret>[\"42\", \"42\"]");
      Assertions.assertEquals(2, results.size());
      for (GroovyResolveResult result : results) {
        Assertions.assertEquals(Applicability.applicable, ((GroovyMethodResult)result).getApplicability());
      }
    });
  }

  @Test
  public void highlighting() {
    runInEdtAndWait(() -> {
      getFixture().enableInspections(GroovyAssignabilityCheckInspection.class);
      getFixture().testHighlighting("highlighting/LiteralConstructorUsages.groovy");
    });
  }
}
