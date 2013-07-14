/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.compiler

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author peter
 */
public abstract class GppCompilerTest extends GroovyCompilerTestCase {
  String[] oldPatterns

  @Override protected void setUp() {
    super.setUp();
    PsiTestUtil.addLibrary myFixture.module, "gpp", TestUtils.absoluteTestDataPath + "/realGroovypp/", "groovy-all-1.8.2.jar", "groovypp-all-0.9.0_1.8.2.jar"
    CompilerConfigurationImpl conf = CompilerConfiguration.getInstance(project)
    oldPatterns = conf.resourceFilePatterns
    conf.addResourceFilePattern("!*.gpp")
  }

  @Override
  protected void tearDown() {
    CompilerConfigurationImpl conf = CompilerConfiguration.getInstance(project)
    conf.removeResourceFilePatterns()
    oldPatterns.each { conf.addResourceFilePattern(it) }
    super.tearDown()
  }

  public void testRecompileDependentGroovyClasses() throws Exception {
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
  
  public void testRecompileDependentJavaClasses() throws Exception {
    def a = myFixture.addFileToProject("A.gpp", """
class A {
  void foo() {
    print "239"
  }
}
""")
    myFixture.addFileToProject("B.java", """
public class B {
  public static void main(String[] args) {
    new A().foo();
  }
}
""")
    assertEmpty make()
    assertOutput "B", "239"

    VfsUtil.saveText a.virtualFile, """
class A {
  def foo() {
    print "239"
  }
}
"""

    assertEmpty make()
    assertOutput "B", "239"
  }

  public static class IdeaModeTest extends GppCompilerTest {
    @Override
    protected boolean useJps() { false }
  }

  public static class JpsModeTest extends GppCompilerTest {
    @Override
    protected boolean useJps() { true }

    @Override
    void testRecompileDependentJavaClasses() {
      super.testRecompileDependentJavaClasses()
    }

    @Override
    void testRecompileDependentGroovyClasses() {
      super.testRecompileDependentGroovyClasses()
    }

    @Override
    protected void tearDown() {
      File systemRoot = BuildManager.getInstance().getBuildSystemDirectory()
      try {
        super.tearDown()
      }
      finally {
        FileUtil.delete(systemRoot);
      }
    }
  }


}
