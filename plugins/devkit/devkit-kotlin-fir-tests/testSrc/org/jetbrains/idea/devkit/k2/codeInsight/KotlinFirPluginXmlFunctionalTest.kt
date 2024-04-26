package org.jetbrains.idea.devkit.k2.codeInsight

import com.intellij.openapi.application.PluginPathManager
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.PathUtil
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.devkit.inspections.PluginXmlDomInspection
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts

class KotlinFirPluginXmlFunctionalTest : JavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(PluginXmlDomInspection::class.java)
  }

  override fun getBasePath(): @NonNls String? {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/devkit-kotlin-fir-tests/testData/codeInsight"
  }

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    //include kotlin in the same library as java annotations
    //otherwise annotation targets are not converted, see `buildEnumCall` at `org/jetbrains/kotlin/fir/java/javaAnnotationsMapping.kt:144`
    //because kotlin builtins are not found in library session
    moduleBuilder.addLibrary("annotations", TestKotlinArtifacts.kotlinStdlib.canonicalPath, PathUtil.getJarPathForClass(XCollection::class.java))
    moduleBuilder.addJdk(IdeaTestUtil.getMockJdk18Path().getPath())
  }

  fun testCustomAttribute() {
    myFixture.testHighlightingAllFiles(true, false, false, "MyBean.kt", "plugin.xml")
  }
}

