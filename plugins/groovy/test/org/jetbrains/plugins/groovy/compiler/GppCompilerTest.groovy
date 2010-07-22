/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.compiler;


import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author peter
 */
public class GppCompilerTest extends GroovyCompilerTestCase {
  @Override protected void setUp() {
    super.setUp();
    PsiTestUtil.addLibrary myFixture.module, "gpp", TestUtils.absoluteTestDataPath + "/realGroovypp/", "groovypp-all-0.2.13.jar"
  }

  public void testTraitStubs() throws Throwable {
    myFixture.addFileToProject("A.groovy", """
@Trait
abstract class SomeTrait {
  abstract def some()
  def concrete() {}
}

@Trait
abstract class AnotherTrait extends SomeTrait {
  abstract def another()
}

class Goo implements SomeTrait {
  def some() {}
}
class Bar implements AnotherTrait {
  def some() {}
  def another() {}
}
""");
    myFixture.addClass("""
class Foo implements SomeTrait {
  public Object some() { return null; }
  public Object concrete() { return null; }
}""")
    assertEmpty(make());
  }

  public void _testRecompileDependentGroovyClasses() throws Exception {
    def a = myFixture.addFileToProject("A.gpp", """
class A {
  void foo() {
    print "239"
  }
}
""")
    myFixture.addFileToProject("b.gpp", """
new A().foo()
""")
    assertEmpty make()
    assertOutput "b", "239"

    VfsUtil.saveText a.virtualFile, """
class A {
  def foo() {
    print "239"
  }
}
"""

    assertEmpty make()
    assertOutput "b", "239"
  }
  
  public void _testRecompileDependentJavaClasses() throws Exception {
    def a = myFixture.addFileToProject("A.gpp", """
class A {
  void foo() {
    print "239"
  }
}
""")
    myFixture.addFileToProject("B.gpp", """
public class B {
  public static void main() {
    new A().foo();
  }
}
""")
    assertEmpty make()
    assertOutput "b", "239"

    VfsUtil.saveText a.virtualFile, """
class A {
  def foo() {
    print "239"
  }
}
"""

    assertEmpty make()
    assertOutput "b", "239"
  }

}
