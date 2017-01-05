/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.transformations.indexedProperty

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection

@CompileStatic
class IndexedPropertyTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  void 'test all methods resolved'() {
    fixture.with {
      addFileToProject 'A.groovy', '''\
import groovy.transform.IndexedProperty
class A {
  @IndexedProperty List rawList
  @IndexedProperty List<String> stringList
  @IndexedProperty Double[] doubleArray
  @IndexedProperty long[] primitiveArray  
}
'''

      configureByText '_.groovy', '''\
def a = new A()
a.getRawList(0)
a.setRawList(1, new Object())

a.getStringList(0)
a.setStringList(1, "Hello")

a.getDoubleArray(1)
a.setDoubleArray(0, 1)

a.getPrimitiveArray(1)
a.setPrimitiveArray(1, 1)
'''
      enableInspections(GrUnresolvedAccessInspection)
      checkHighlighting()

      configureByText 'Main.java', '''\
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
'''
      checkHighlighting()
    }
  }

  void 'test errors highlighting'() {
    fixture.with {
      configureByText '_.groovy', '''\
import groovy.transform.IndexedProperty
class A {
    @IndexedProperty List rawList
    @IndexedProperty List<String> stringList
    @IndexedProperty Double[] doubleArray
    @IndexedProperty long[] primitiveArray
    @IndexedProperty <error descr="Property is not indexable. Type must be array or list but found Collection<Number>">Collection<Number></error> numberCollection
    @IndexedProperty <error descr="Property is not indexable. Type must be array or list but found Object">untyped</error>
    @IndexedProperty <error descr="Property is not indexable. Type must be array or list but found Integer">Integer</error> nonIndexable    
    private <error descr="@IndexedProperty is applicable to properties only">@IndexedProperty</error> explicitVisibility 
}
'''
      checkHighlighting()
    }
  }

  void 'test indexed property unused'() {
    fixture.with {
      configureByText '_.groovy', '''\
import groovy.transform.IndexedProperty
class A {
  @IndexedProperty List<String> <warning descr="Property stringList is unused">stringList</warning>
}
new A()
'''
      enableInspections(GroovyUnusedDeclarationInspection)
      checkHighlighting()
    }
  }

  void 'test indexed property is used via generated method'() {
    fixture.with {
      configureByText '_.groovy', '''\
import groovy.transform.IndexedProperty
class A {
  @IndexedProperty List<String> strin<caret>gList
}
new A().getStringList(0)
'''
      enableInspections(GroovyUnusedDeclarationInspection)
      checkHighlighting()
    }
  }

  void 'test indexed property rename'() {
    fixture.with {
      configureByText '_.groovy', '''\
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
'''
      renameElementAtCaret 'newName'
      checkResult '''\
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
'''
    }
  }
}
