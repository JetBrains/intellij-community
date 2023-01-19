// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.gant

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.LibraryLightProjectDescriptor
import org.jetbrains.plugins.groovy.RepositoryTestLibrary
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GroovyUntypedAccessInspection
import org.jetbrains.plugins.groovy.util.TestUtils

import static org.jetbrains.plugins.groovy.GroovyProjectDescriptors.LIB_GROOVY_1_7

@CompileStatic
class GantReferenceCompletionTest extends LightJavaCodeInsightFixtureTestCase {

  private static final LightProjectDescriptor GANT_PROJECT = new LibraryLightProjectDescriptor(
    LIB_GROOVY_1_7 + new RepositoryTestLibrary('org.codehaus.gant:gant_groovy1.7:1.9.7')
  )

  final LightProjectDescriptor projectDescriptor = GANT_PROJECT
  final String basePath = TestUtils.testDataPath + "gant/completion"

  void complete(String text) {
    myFixture.configureByText "a.gant", text
    myFixture.completeBasic()
  }

  void checkVariants(String text, String... items) {
    complete text
    assert myFixture.lookupElementStrings.containsAll(items as List)
  }

  void testDep() {
    checkVariants """
target(aaa: "") {
    dep<caret>
}
""", "depends", "dependset"
  }

  void testAntBuilderJavac() {
    checkVariants """
target(aaa: "") {
    ant.jav<caret>
}""", "java", "javac", "javadoc", "javadoc2", "javaresource"
  }

  void testAntJavacTarget() {
    checkVariants """
target(aaa: "") {
    jav<caret>
}""", "java", "javac", "javadoc", "javadoc2", "javaresource"
  }

  void testInclude() {
    checkVariants "inc<caret>", "include", "includeTool", "includeTargets"
  }

  void testMutual() {
    checkVariants """
target(genga: "") { }
target(aaa: "") {
    depends(geng<caret>x)
}""", 'genga'
  }

  void testUnknownQualifier() {
    complete """
target(aaa: "") {
    foo.jav<caret>
}"""
  }

  void testTopLevelNoAnt() {
    complete "jav<caret>"
  }

  void testInMethodNoAnt() {
    complete """
target(aaa: "") {
  foo()
}

def foo() {
  jav<caret>
}
"""
  }

  void testPatternset() throws Exception {
    checkVariants "ant.patt<caret>t", "patternset"
  }

  void testTagsInsideTags() throws Exception {
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

  void testTagsInsideTagsInGantTarget() throws Exception {
    checkVariants """
target(aaa: "") {
  zip {
    patternset {
      includ<caret>
    }
  }
}""", "include", "includesfile", "includeTargets", "includeTool"
  }

  void testUntypedTargets() throws Exception {
    myFixture.enableInspections(new GroovyUntypedAccessInspection())

    myFixture.configureByText "a.gant", """
target (default : '') {
        echo(message: 'Echo task.')
        copy(file: 'from.txt', tofile: 'to.txt')
        delete(file: 'to.txt')
}"""
    myFixture.checkHighlighting(true, false, false)
  }

  void testStringTargets() throws Exception {
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
}
