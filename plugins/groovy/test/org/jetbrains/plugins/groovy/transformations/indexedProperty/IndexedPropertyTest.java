// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.indexedProperty;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;

public class IndexedPropertyTest extends LightGroovyTestCase {
  @Override
  @NotNull
  public final LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  public void testAllMethodsResolved() {
    myFixture.addFileToProject("A.groovy", """
      import groovy.transform.IndexedProperty
      class A {
        @IndexedProperty List rawList
        @IndexedProperty List<String> stringList
        @IndexedProperty Double[] doubleArray
        @IndexedProperty long[] primitiveArray
      }
      """);

    myFixture.configureByText("_.groovy", """
      def a = new A()
      a.getRawList(0)
      a.setRawList(1, new Object())
      
      a.getStringList(0)
      a.setStringList(1, "Hello")
      
      a.getDoubleArray(1)
      a.setDoubleArray(0, 1)
      
      a.getPrimitiveArray(1)
      a.setPrimitiveArray(1, 1)
      """);
    myFixture.enableInspections(GrUnresolvedAccessInspection.class);
    myFixture.checkHighlighting();

    myFixture.configureByText("Main.java", """
      class Main {
        void foo() {
          A a = new A();
          a.getRawList(0);
          a.setRawList(1, new Object());
      
          a.getStringList(0);
          a.setStringList(1, "Hello");
      
          a.getDoubleArray(1);
          a.setDoubleArray(0, (double)1);
      
          a.getPrimitiveArray(1);
          a.setPrimitiveArray(1, 1L);
        }
      }
      """);
    myFixture.checkHighlighting();
  }

  public void testErrorsHighlighting() {
    myFixture.configureByText("_.groovy", """
      import groovy.transform.IndexedProperty
      class A {
          @IndexedProperty List rawList
          @IndexedProperty List<String> stringList
          @IndexedProperty Double[] doubleArray
          @IndexedProperty long[] primitiveArray
          <error descr="Property is not indexable. Type must be an array or a list but found Collection<Number>">@IndexedProperty</error> Collection<Number> numberCollection
          <error descr="Property is not indexable. Type must be an array or a list but found Object">@IndexedProperty</error> untyped
          <error descr="Property is not indexable. Type must be an array or a list but found Integer">@IndexedProperty</error> Integer nonIndexable
          private <error descr="@IndexedProperty is applicable to properties only">@IndexedProperty</error> explicitVisibility
      }
      """);
    myFixture.checkHighlighting();
  }

  public void testIndexedPropertyUnused() {
    myFixture.configureByText("_.groovy", """
      import groovy.transform.IndexedProperty
      class A {
        @IndexedProperty List<String> <warning descr="Property stringList is unused">stringList</warning>
      }
      new A()
      """);
    myFixture.enableInspections(GroovyUnusedDeclarationInspection.class);
    myFixture.checkHighlighting();
  }

  public void testIndexedPropertyIsUsedViaGeneratedMethod() {
    myFixture.configureByText("_.groovy", """
      import groovy.transform.IndexedProperty
      class A {
        @IndexedProperty List<String> strin<caret>gList
      }
      new A().getStringList(0)
      """);
    myFixture.enableInspections(GroovyUnusedDeclarationInspection.class);
    myFixture.checkHighlighting();
  }

  public void testIndexedPropertyRename() {
    myFixture.configureByText("_.groovy", """
      import groovy.transform.IndexedProperty
      class A {
        @IndexedProperty List<String> strin<caret>gList
      }
      def a = new A()
      a.stringList
      a.getStringList()
      a.stringList = []
      a.setStringList([])
      a.getStringList(0)
      a.setStringList(0, "")
      """);
    myFixture.renameElementAtCaret("newName");
    myFixture.checkResult("""
      import groovy.transform.IndexedProperty
      class A {
        @IndexedProperty List<String> newName
      }
      def a = new A()
      a.newName
      a.getNewName()
      a.newName = []
      a.setNewName([])
      a.getNewName(0)
      a.setNewName(0, "")
      """);
  }
}
