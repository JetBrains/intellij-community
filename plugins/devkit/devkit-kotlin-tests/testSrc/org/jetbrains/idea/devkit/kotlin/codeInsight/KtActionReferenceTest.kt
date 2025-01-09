// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.codeInsight

import com.intellij.codeInspection.LocalInspectionEP
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.ui.components.JBList
import com.intellij.util.PathUtil
import org.jetbrains.idea.devkit.inspections.UnresolvedPluginConfigReferenceInspection

class KtActionReferenceTest : JavaCodeInsightFixtureTestCase() {

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    moduleBuilder.addLibrary("platform-ide", PathUtil.getJarPathForClass(JBList::class.java))
    moduleBuilder.addLibrary("platform-editor", PathUtil.getJarPathForClass(ActionManager::class.java))
    moduleBuilder.addLibrary("platform-resources", PathManager.getResourceRoot(LocalInspectionEP::class.java, "/idea/PlatformActions.xml")!!)
  }

  private fun pluginXmlActions(actionsText: String): String {
    return """
      <idea-plugin>
        <actions>
        ${actionsText.trimIndent()}
        </actions>
      </idea-plugin>  
    """
  }

  fun testResolveDuplicateActionId() {
    val pluginXmlActions = pluginXmlActions("""
      <action id="myAction"/>
    """.trimIndent())
    myFixture.createFile("plugin.xml", pluginXmlActions)
    myFixture.createFile("anotherPlugin.xml", pluginXmlActions)
    myFixture.configureByText("Caller.kt", """
      fun usage(actionManager: com.intellij.openapi.actionSystem.ActionManager){
      
         actionManager.getAction("my<caret>Action")
      
      }
    """.trimIndent())
    myFixture.enableInspections(UnresolvedPluginConfigReferenceInspection::class.java)
    myFixture.testHighlighting()
  }

  fun testResolveLibraryActionId() {
    val pluginXmlActions = pluginXmlActions("""
      <action id="myAction"/>
    """.trimIndent())
    myFixture.createFile("plugin.xml", pluginXmlActions)
    myFixture.createFile("anotherPlugin.xml", pluginXmlActions)
    myFixture.configureByText("Caller.kt", """
      fun usage(actionManager: com.intellij.openapi.actionSystem.ActionManager){
      
         actionManager.getAction("$DLR{"\$DLR"}Undo")
         actionManager.getAction("Find")
         actionManager.getAction("<error>Unknown1</error>")
      
      }
    """.trimIndent())
    myFixture.enableInspections(UnresolvedPluginConfigReferenceInspection::class.java)
    myFixture.testHighlighting()
  }

  fun testRenameAction() {
    myFixture.createFile("plugin.xml", pluginXmlActions("""
              <group id="myGroup"></group>
              <action id="myAction" class="foo.bar.BarAction"></action>
              """
    ));
    myFixture.configureByText("Caller.kt", """
      fun usage(actionManager: com.intellij.openapi.actionSystem.ActionManager){
      
         actionManager.getAction("my<caret>Action")
      
      }
    """.trimIndent())
    myFixture.renameElementAtCaret("myActionRenamed")
    myFixture.checkResult("plugin.xml", pluginXmlActions("""
              <group id="myGroup"></group>
              <action id="myActionRenamed" class="foo.bar.BarAction"></action>
              """
    ), true)
  }

  fun testCompletion() {
    myFixture.createFile("plugin.xml", pluginXmlActions("""
              <group id="myGroup"></group>
              <action id="myAction" class="foo.bar.BarAction"></action>
              """
    ));
    myFixture.configureByText("Caller.kt", """
      fun usage(actionManager: com.intellij.openapi.actionSystem.ActionManager){
      
         actionManager.getAction("my<caret>")
      
      }
    """.trimIndent())
    assertSameElements(myFixture.getCompletionVariants("Caller.kt").orEmpty(), "myAction", "myGroup")
  }

  fun testInvalidActionOrGroupReference() {
    myFixture.enableInspections(UnresolvedPluginConfigReferenceInspection::class.java)
    myFixture.createFile("plugin.xml", pluginXmlActions("""
              <group id="myGroup"></group>
              <action id="myAction" class="foo.bar.BarAction"></action>
              <action id="${DLR}myActionDollar" class="foo.bar.BarAction"></action>
              """
    ));
    myFixture.configureByText("Caller.kt", """
      fun usage(actionManager: com.intellij.openapi.actionSystem.ActionManager){
      
         actionManager.getAction("myAction")
         actionManager.getAction("$DLR{"\$DLR"}myActionDollar")
         actionManager.getAction("<error descr="Cannot resolve action id 'someUndefinedAction'">someUndefinedAction</error>")
      
      }
    """.trimIndent())
    myFixture.testHighlighting()
  }

  fun testRenameGroup() {
    myFixture.createFile("plugin.xml", pluginXmlActions("""
              <group id="myGroup"></group>
              <action id="myAction" class="foo.bar.BarAction"></action>
              """
    ));
    myFixture.configureByText("Caller.kt", """
      fun usage(actionManager: com.intellij.openapi.actionSystem.ActionManager){
      
         actionManager.getAction("my<caret>Group")
      
      }
    """.trimIndent())
    myFixture.renameElementAtCaret("myGroupRenamed")
    myFixture.checkResult("plugin.xml", pluginXmlActions("""
              <group id="myGroupRenamed"></group>
              <action id="myAction" class="foo.bar.BarAction"></action>
              """
    ), true)
  }

  private val DLR = '$'.toString()

}