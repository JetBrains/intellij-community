// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang

import com.intellij.openapi.util.RecursionManager
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyConstructorResult
import org.jetbrains.plugins.groovy.util.HighlightingTest
import org.jetbrains.plugins.groovy.util.LightProjectTest
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.jetbrains.plugins.groovy.util.TestUtils
import org.junit.Assert
import org.junit.Before
import org.junit.Test

import static com.intellij.testFramework.UsefulTestCase.assertInstanceOf
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

@CompileStatic
class LiteralConstructorTest extends LightProjectTest implements HighlightingTest, ResolveTest {

  @Override
  LightProjectDescriptor getProjectDescriptor() {
    GroovyProjectDescriptors.GROOVY_2_5_REAL_JDK
  }

  @Before
  void disableRecursion() {
    RecursionManager.assertOnRecursionPrevention(fixture.testRootDisposable)
  }

  @Before
  void addClasses() {
    fixture.addFileToProject('classes.groovy', '''\
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
''')
  }

  @Test
  void 'constructor reference'() {
    def data = [
      'boolean boo = [<caret>]'       : false,
      'boolean boo = [<caret>:]'      : false,
      'byte b = [<caret>]'            : false,
      'byte b = [<caret>:]'           : false,
      'char c = [<caret>]'            : false,
      'char c = [<caret>:]'           : false,
      'double d = [<caret>]'          : false,
      'double d = [<caret>:]'         : false,
      'float f = [<caret>]'           : false,
      'float f = [<caret>:]'          : false,
      'int i = [<caret>]'             : false,
      'int i = [<caret>:]'            : false,
      'long l = [<caret>]'            : false,
      'long l = [<caret>:]'           : false,
      'short s = [<caret>]'           : false,
      'short s = [<caret>:]'          : false,
      'void v = [<caret>]'            : false,
      'void v = [<caret>:]'           : false,

      'Boolean boo = [<caret>]'       : false,
      'Boolean boo = [<caret>:]'      : false,
      'Byte b = [<caret>]'            : false,
      'Byte b = [<caret>:]'           : false,
      'Character c = [<caret>]'       : false,
      'Character c = [<caret>:]'      : false,
      'Double d = [<caret>]'          : false,
      'Double d = [<caret>:]'         : false,
      'Float f = [<caret>]'           : false,
      'Float f = [<caret>:]'          : false,
      'Integer i = [<caret>]'         : false,
      'Integer i = [<caret>:]'        : false,
      'Long l = [<caret>]'            : false,
      'Long l = [<caret>:]'           : false,
      'Short s = [<caret>]'           : false,
      'Short s = [<caret>:]'          : false,
      'Void v = [<caret>]'            : true, // wtf
      'Void v = [<caret>:]'           : true, // wtf

      'def a = [<caret>]'             : false,
      'def a = [<caret>:]'            : false,
      'Object a = [<caret>]'          : false,
      'Object a = [<caret>:]'         : false,

      'Class a = [<caret>]'           : false,
      'Class a = [<caret>:]'          : false,
      'String s = [<caret>]'          : false,
      'String s = [<caret>:]'         : false,

      'En e = [<caret>]'              : false,
      'En e = [<caret>:]'             : false,

      'Collection c = [<caret>]'      : false,
      'Collection c = [<caret>:]'     : true,

      'List l = [<caret>]'            : false,
      'List l = [<caret>:]'           : true,
      'ArrayList c = [<caret>]'       : false,
      'ArrayList c = [<caret>:]'      : true,

      'Map m = [<caret>]'             : true,
      'Map m = [<caret>:]'            : false,
      'HashMap hm = [<caret>]'        : true,
      'HashMap hm = [<caret>:]'       : false,
      'LinkedHashMap lhm = [<caret>]' : true,
      'LinkedHashMap lhm = [<caret>:]': false,

      'Set s = [<caret>]'             : false,
      'AbstractSet aS = [<caret>]'    : false,
      'HashSet hs = [<caret>]'        : true,
      'LinkedHashSet lhs = [<caret>]' : false,

      'Set s = [<caret>:]'            : true,
      'HashSet hs = [<caret>:]'       : true,
      'LinkedHashSet lhs = [<caret>:]': true,

      'Person p = [<caret>]'          : true,
      'Person p = [<caret>:]'         : true,
    ]
    constructorReferenceTest(data)
  }

  @Test
  void 'constructor reference @CS'() {
    def data = [
      'boolean boo = [<caret>]'   : false,
      'byte b = [<caret>]'        : false,
      'char c = [<caret>]'        : false,
      'double d = [<caret>]'      : false,
      'float f = [<caret>]'       : false,
      'int i = [<caret>]'         : false,
      'long l = [<caret>]'        : false,
      'short s = [<caret>]'       : false,
      'void v = [<caret>]'        : false,

      'boolean boo = [<caret>1]'  : true,
      'byte b = [<caret>1]'       : true,
      'char c = [<caret>1]'       : true,
      'double d = [<caret>1]'     : true,
      'float f = [<caret>1]'      : true,
      'int i = [<caret>1]'        : true,
      'long l = [<caret>1]'       : true,
      'short s = [<caret>1]'      : true,
      'void v = [<caret>1]'       : true,

      'boolean boo = [<caret>:]'  : true,
      'byte b = [<caret>:]'       : true,
      'char c = [<caret>:]'       : true,
      'double d = [<caret>:]'     : true,
      'float f = [<caret>:]'      : true,
      'int i = [<caret>:]'        : true,
      'long l = [<caret>:]'       : true,
      'short s = [<caret>:]'      : true,
      'void v = [<caret>:]'       : true,

      'Boolean boo = [<caret>]'   : true,
      'Byte b = [<caret>]'        : true,
      'Character c = [<caret>]'   : true,
      'Double d = [<caret>]'      : true,
      'Float f = [<caret>]'       : true,
      'Integer i = [<caret>]'     : true,
      'Long l = [<caret>]'        : true,
      'Short s = [<caret>]'       : true,
      'Void v = [<caret>]'        : true,

      'Boolean boo = [<caret>:]'  : true,
      'Byte b = [<caret>:]'       : true,
      'Character c = [<caret>:]'  : true,
      'Double d = [<caret>:]'     : true,
      'Float f = [<caret>:]'      : true,
      'Integer i = [<caret>:]'    : true,
      'Long l = [<caret>:]'       : true,
      'Short s = [<caret>:]'      : true,
      'Void v = [<caret>:]'       : true,

      'BigInteger bi = [<caret>]' : true,
      'BigInteger bi = [<caret>1]': true,
      'BigInteger bi = [<caret>:]': true,

      'def a = [<caret>]'         : false,
      'def a = [<caret>:]'        : false,
      'Object a = [<caret>]'      : false,
      'Object a = [<caret>:]'     : false,

      'Class a = [<caret>]'       : false,
      'Class a = [<caret>:]'      : false,
      'String s = [<caret>]'      : true,
      'String s = [<caret>:]'     : true,

      'Set s = [<caret>]'         : false,
      'Set s = [<caret>:]'        : true,

      'Person p = [<caret>]'      : true,
      'Person p = [<caret>:]'     : true,
    ]
    constructorReferenceTest(data, true)
  }

  @Test
  void 'constructor reference from named argument'() {
    def data = [
      'Person p = [parent: [<caret>]]'           : true,
      'Person p = [parent: [<caret>:]]'          : true,
      'Person p = new Person(parent: [<caret>]])': true,
      'Person p = new Person(parent: [<caret>:])': true,
      'M m = [abc:[<caret>:]]'                   : false,
    ]
    constructorReferenceTest(data)
  }

  @Test
  void 'constructor reference from safe cast'() {
    def data = [
      '[<caret>] as List'                      : false,
      '[<caret>] as Set'                       : false,
      '[<caret>] as SortedSet'                 : false,
      '[<caret>] as Queue'                     : false,
      '[<caret>] as Stack'                     : false,
      '[<caret>] as LinkedList'                : false,
      '[<caret>] as CollectionConstructor'     : false,
      '[<caret>] as CollectionNoArgConstructor': false,
      '[<caret>] as String'                    : false,
      '[<caret>:] as List'                     : false,
      '[<caret>] as Person'                    : true,
      '[<caret>:] as Person'                   : true,
    ]
    constructorReferenceTest(data)
  }

  private void constructorReferenceTest(LinkedHashMap<String, Boolean> data, boolean cs = false) {
    TestUtils.runAll(data) { text, expected ->
      def fullText = cs ? (String)"@groovy.transform.CompileStatic def cs() { $text }" : text
      def listOrMap = elementUnderCaret(fullText, GrListOrMap)
      def reference = listOrMap.constructorReference
      if (expected) {
        Assert.assertNotNull(text, reference)
      }
      else {
        Assert.assertNull(text, reference)
      }
    }
  }

  @Test
  void 'property reference'() {
    def data = [
      'Person p = [<caret>parent: [parent: []]]'          : true,
      'Person p = [parent: [<caret>parent: []]]'          : true,
      'Person p = new Person(<caret>parent: [parent: []])': true,
      'Person p = new Person(parent: [<caret>parent: []])': true,
      'C c = [<caret>a: [a: []]]'                         : true,
      'C c = [a: [<caret>a: []]]'                         : false,
      'M m = [<caret>abc: []]'                            : false,
      'foo(<caret>abc: 1)'                                : false,
    ]
    TestUtils.runAll(data) { text, expected ->
      def label = elementUnderCaret(text, GrArgumentLabel)
      def reference = label.constructorPropertyReference
      if (expected) {
        Assert.assertNotNull(text, reference)
      }
      else {
        Assert.assertNull(text, reference)
      }
    }
  }

  @Test
  void 'resolve constructor applicable'() {
    def data = [
      'Person p = <caret>[]'  : [0, false], // default
      'Person p = <caret>[:]' : [0, true],  // default (map-constructor)
      'C c = <caret>[]'       : [0, false], // no param
      'C c = <caret>[1, "42"]': [2, false], // two param
      'C c = <caret>[:]'      : [0, true],  // no param (map-constructor)
      'C c = <caret>[a: 1]'   : [0, true],  // no param (map-constructor)
      'M m = <caret>[]'       : [0, false], // no param
      'M m = <caret>[:]'      : [1, false], // single Map param
      'M m = <caret>[a: 1]'   : [1, false], // single Map param
    ]
    TestUtils.runAll(data) { text, expected ->
      int parametersCount = expected[0] as int
      boolean mapConstructor = expected[1]
      def result = assertInstanceOf(advancedResolveByText(text), GroovyConstructorResult)
      assertEquals(text, mapConstructor, result.mapConstructor)
      assertEquals(text, Applicability.applicable, result.applicability)
      def method = result.element
      assertTrue(text, method.constructor)
      assertEquals(text, parametersCount, method.parameterList.parametersCount)
    }
  }

  @Test
  void 'resolve constructor inapplicable'() {
    def data = [
      'C c = <caret>[1]'                     : 3,
      'NoDefaultConstructor ndc = <caret>[]' : 1,
      'NoDefaultConstructor ndc = <caret>[:]': 1,
    ]
    TestUtils.runAll(data) { text, resultsCount ->
      def results = multiResolveByText(text)
      assert results.size() == resultsCount
      results.each {
        assert ((GroovyMethodResult)it).applicability == Applicability.inapplicable
      }
    }
  }

  @Test
  void 'resolve list literal ambiguous'() {
    def results = multiResolveByText('C c = <caret>["42", "42"]')
    assert results.size() == 2
    results.each {
      assert ((GroovyMethodResult)it).applicability == Applicability.applicable
    }
  }

  @Test
  void 'highlighting'() {
    fixture.enableInspections(GroovyAssignabilityCheckInspection)
    fixture.testHighlighting('highlighting/LiteralConstructorUsages.groovy')
  }
}
