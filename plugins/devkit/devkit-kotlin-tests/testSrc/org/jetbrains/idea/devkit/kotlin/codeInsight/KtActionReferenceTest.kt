// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.codeInsight

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInspection.LocalInspectionEP
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.options.Scheme
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.ui.components.JBList
import com.intellij.util.PathUtil
import org.jetbrains.idea.devkit.inspections.UnresolvedPluginConfigReferenceInspection
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/codeInsight/actionReference")
class KtActionReferenceTest : JavaCodeInsightFixtureTestCase() {

  override fun getBasePath(): String {
    return DevkitKtTestsUtil.TESTDATA_PATH + "codeInsight/actionReference"
  }

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    moduleBuilder.addLibrary("platform-core", PathUtil.getJarPathForClass(Scheme::class.java))
    moduleBuilder.addLibrary("platform-ide", PathUtil.getJarPathForClass(JBList::class.java))
    moduleBuilder.addLibrary("platform-editor", PathUtil.getJarPathForClass(ActionManager::class.java))
    moduleBuilder.addLibrary("execution", PathUtil.getJarPathForClass(DefaultRunExecutor::class.java))
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

  fun testActionReferenceHighlighting() {
    myFixture.enableInspections(UnresolvedPluginConfigReferenceInspection::class.java)
    myFixture.createFile("plugin.xml", pluginXmlActions("""
              <group id="myGroup"></group>
              <action id="myAction" class="foo.bar.BarAction"></action>
              """
    ))
    myFixture.addClass("""
      package java.awt.event;
      public class KeyEvent {}
    """.trimIndent())

    myFixture.testHighlighting("ActionReferenceHighlighting.kt")
  }

  fun testActionReferenceToolWindowHighlighting() {
    myFixture.enableInspections(UnresolvedPluginConfigReferenceInspection::class.java)
    configureToolWindowTest()

    myFixture.testHighlighting("ActionReferenceToolWindowHighlighting.kt")
  }

  fun testActionReferenceToolWindowCompletion() {
    configureToolWindowTest()
    myFixture.configureByText("Caller.kt", """
      fun usage(actionManager: com.intellij.openapi.actionSystem.ActionManager){
        actionManager.getAction("ActivateT<caret>")
      }
    """.trimIndent())

    assertContainsElements(myFixture.getCompletionVariants("Caller.kt").orEmpty(),
                           "ActivateToolWindowIdToolWindow", "ActivateToolWindowIdWithSpacesToolWindow",
                           "ActivateToolWindowIdFromConstantsToolWindow", "ActivateToolWindowIdFromConstants_DeprecatedToolWindow")

    val extension = getLookupElementPresentation("ActivateToolWindowIdToolWindow")
    assertTrue(extension.isItemTextBold)
    assertEquals("FactoryClass", extension.typeText)

    val deprecated = getLookupElementPresentation("ActivateToolWindowIdFromConstants_DeprecatedToolWindow")
    assertTrue(deprecated.isStrikeout)
    assertEquals("com.intellij.openapi.wm.ToolWindowId#DEPRECATED", deprecated.typeText)
  }

  private fun configureToolWindowTest() {
    myFixture.copyFileToProject("actionReferenceToolWindowHighlighting.xml")
    myFixture.addClass("""
        package com.intellij.openapi.wm;
        interface ToolWindowId {
          String FAVORITES = "ToolWindowIdFromConstants";
          @Deprecated
          String DEPRECATED = "ToolWindowIdFromConstants_Deprecated";        
        }
      """.trimIndent())
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

  private fun getLookupElementPresentation(lookupString: String): LookupElementPresentation {
    val lookupElement: LookupElement? = myFixture.getLookupElements()!!.find({ element: LookupElement -> element.getLookupString() == lookupString })
    assertNotNull(lookupString, lookupElement)
    return LookupElementPresentation.renderElement(lookupElement)
  }
}
