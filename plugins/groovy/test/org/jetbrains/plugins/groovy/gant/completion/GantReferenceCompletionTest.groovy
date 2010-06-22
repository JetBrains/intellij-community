package org.jetbrains.plugins.groovy.gant.completion;


import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author ilyas
 */
public class GantReferenceCompletionTest extends LightCodeInsightFixtureTestCase {
  static def descriptor = new GantProjectDescriptor()

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
    checkVariants """
target(aaa: "") {
    dep<caret>
}
""", "depends", "dependSet"
  }

  public void testAntBuilderJavac() throws Throwable {
    checkVariants """
target(aaa: "") {
    ant.jav<caret>
}""", "java", "javac", "javadoc"
  }

  public void testAntJavacTarget() throws Throwable {
    checkVariants """
target(aaa: "") {
    jav<caret>
}""", "java", "javac", "javadoc"
  }

  public void testInclude() throws Throwable {
    checkVariants "inc<caret>", "includeTool", "includeTargets"
  }

  public void testMutual() throws Throwable {
    complete """
target(genga: "") { }
target(aaa: "") {
    depends(geng<caret>)
}"""
    myFixture.checkResult """
target(genga: "") { }
target(aaa: "") {
    depends(genga<caret>)
}"""
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

  static final def GANT_JARS = ["gant.jar", "ant.jar", "ant-junit.jar", "commons.jar"]

}

class GantProjectDescriptor implements LightProjectDescriptor {
  public ModuleType getModuleType() {
      return StdModuleTypes.JAVA;
    }

    public Sdk getSdk() {
      return JavaSdkImpl.getMockJdk15("java 1.5");
    }

    public void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
      final Library.ModifiableModel modifiableModel = model.getModuleLibraryTable().createLibrary("GROOVY").getModifiableModel();

      def fs = JarFileSystem.instance
      modifiableModel.addRoot(fs.findFileByPath("$TestUtils.mockGroovyLibraryHome/$TestUtils.GROOVY_JAR!/"), OrderRootType.CLASSES);

      GantReferenceCompletionTest.GANT_JARS.each {
        modifiableModel.addRoot(fs.findFileByPath("${TestUtils.absoluteTestDataPath}mockGantLib/$it!/"), OrderRootType.CLASSES);
      }
      modifiableModel.commit();
    }
}