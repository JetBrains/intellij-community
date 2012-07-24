/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.gant;


import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GroovyUntypedAccessInspection
import org.jetbrains.plugins.groovy.util.TestUtils
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher

/**
 * @author ilyas
 */
public class GantReferenceCompletionTest extends LightCodeInsightFixtureTestCase {
  static def descriptor = new GantProjectDescriptor()

  @NotNull
  @Override protected LightProjectDescriptor getProjectDescriptor() {
    return descriptor;
  }

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "gant/completion";
  }

  void complete(String text) {
    myFixture.configureByText "a.gant", text
    myFixture.completeBasic()
  }

  void checkVariants(String text, String... items) {
    complete text
    assertSameElements myFixture.lookupElementStrings, items
  }

  public void testDep() throws Throwable {
    CamelHumpMatcher.forceStartMatching(getTestRootDisposable());
    checkVariants """
target(aaa: "") {
    dep<caret>
}
""", "depends", "dependset"
  }

  public void testAntBuilderJavac() throws Throwable {
    checkVariants """
target(aaa: "") {
    ant.jav<caret>
}""", "java", "javac", "javadoc", "javadoc2", "javaresource"
  }

  public void testAntJavacTarget() throws Throwable {
    checkVariants """
target(aaa: "") {
    jav<caret>
}""", "java", "javac", "javadoc", "javadoc2", "javaresource"
  }

  public void testInclude() throws Throwable {
    CamelHumpMatcher.forceStartMatching(getTestRootDisposable());
    checkVariants "inc<caret>", "includeTool", "includeTargets"
  }

  public void testMutual() throws Throwable {
    checkVariants """
target(genga: "") { }
target(aaa: "") {
    depends(geng<caret>x)
}""", 'genga'
  }

  public void testUnknownQualifier() throws Throwable {
    complete """
target(aaa: "") {
    foo.jav<caret>
}"""
  }

  public void testTopLevelNoAnt() throws Throwable {
    complete "jav<caret>"
  }

  public void testInMethodNoAnt() throws Throwable {
    complete """
target(aaa: "") {
  foo()
}

def foo() {
  jav<caret>
}
"""
  }

  public void testPatternset() throws Exception {
    checkVariants "ant.patt<caret>t", "patternset"
  }

  public void testTagsInsideTags() throws Exception {
    myFixture.configureByText "a.groovy", """
AntBuilder ant
ant.zip {
  patternset {
    includ<caret>
  }
}"""
    myFixture.completeBasic()
    assertSameElements myFixture.lookupElementStrings, "include", "includesfile"
  }
  
  public void testTagsInsideTagsInGantTarget() throws Exception {
    CamelHumpMatcher.forceStartMatching(getTestRootDisposable());
    checkVariants """
target(aaa: "") {
  zip {
    patternset {
      includ<caret>
    }
  }
}""", "include", "includesfile", "includeTargets", "includeTool"
  }

  public void testUntypedTargets() throws Exception {
    myFixture.enableInspections(new GroovyUntypedAccessInspection())

    myFixture.configureByText "a.gant", """
target (default : '') {
        echo(message: 'Echo task.')
        copy(file: 'from.txt', tofile: 'to.txt')
        delete(file: 'to.txt')
}"""
    myFixture.checkHighlighting(true, false, false)

  }

  public void testStringTargets() throws Exception {
    myFixture.enableInspections(new GroovyAssignabilityCheckInspection())

    myFixture.configureByText "a.gant", """
target (default : '') {
  echo("hello2")
  echo(message: 'Echo task.')
  ant.fail('Failure reason')
  ant.junit(fork:'yes') {}
}"""
    myFixture.checkHighlighting(true, false, false)
  }

  static final def GANT_JARS = ["gant.jar", "ant.jar", "ant-junit.jar", "ant-launcher.jar", "commons.jar"]

}

class GantProjectDescriptor extends DefaultLightProjectDescriptor {
    public void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
      final Library.ModifiableModel modifiableModel = model.getModuleLibraryTable().createLibrary("GROOVY").getModifiableModel();

      def fs = JarFileSystem.instance
      modifiableModel.addRoot(fs.findFileByPath("$TestUtils.mockGroovyLibraryHome/$TestUtils.GROOVY_JAR!/"), OrderRootType.CLASSES);

      GantReferenceCompletionTest.GANT_JARS.each {
        modifiableModel.addRoot(fs.findFileByPath("${TestUtils.absoluteTestDataPath}mockGantLib/lib/$it!/"), OrderRootType.CLASSES);
      }
      modifiableModel.commit();
    }
}