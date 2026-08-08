// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.junit.Assert

class PluginXmlReferencesModuleReachabilityInspectionTest : JavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, true)
    myFixture.addClass("package com.intellij.openapi.actionSystem; public class AnAction {}")
    myFixture.enableInspections(PluginXmlReferencesModuleReachabilityInspection())
  }

  override fun tearDown() {
    try {
      IntelliJProjectUtil.markAsIntelliJPlatformProject(project, null)
    } catch (e: Throwable) {
      addSuppressedException(e)
    } finally {
      super.tearDown()
    }
  }

  fun `test class in same module - no error`() {
    myFixture.addClass("""package com.example; public class SameModuleAction extends com.intellij.openapi.actionSystem.AnAction {}""")
    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <actions>
              <action class="com.example.SameModuleAction" id="SameModuleAction"/>
          </actions>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test class in dependency module - no error`() {
    val depModule = addModuleWithSourceRoot("depModule")
    ModuleRootModificationUtil.addDependency(myFixture.module, depModule)
    myFixture.addFileToProject(
      "depModule/com/example/DepAction.java",
      //language=JAVA
      "package com.example; public class DepAction extends com.intellij.openapi.actionSystem.AnAction {}"
    )

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <actions>
              <action class="com.example.DepAction" id="DepAction"/>
          </actions>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test class in transitive dependency module - no error`() {
    val transitiveModule = addModuleWithSourceRoot("transitiveModule")
    val directDepModule = addModuleWithSourceRoot("directDepModule")
    ModuleRootModificationUtil.addDependency(directDepModule, transitiveModule, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(myFixture.module, directDepModule)
    myFixture.addFileToProject(
      "transitiveModule/com/example/TransitiveAction.java",
      //language=JAVA
      "package com.example; public class TransitiveAction extends com.intellij.openapi.actionSystem.AnAction {}"
    )

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <actions>
              <action class="com.example.TransitiveAction" id="TransitiveAction"/>
          </actions>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test action class in unrelated module - no error because registration`() {
    addModuleWithSourceRoot("unrelatedModule")
    myFixture.addFileToProject(
      "unrelatedModule/com/example/UnrelatedAction.java",
      //language=JAVA
      "package com.example; public class UnrelatedAction extends com.intellij.openapi.actionSystem.AnAction {}"
    )

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <actions>
              <action class="com.example.UnrelatedAction" id="UnrelatedAction"/>
          </actions>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test mixed reachable and unreachable action classes - no errors because registrations`() {
    myFixture.addClass("""package com.example; public class LocalAction extends com.intellij.openapi.actionSystem.AnAction {}""")
    addModuleWithSourceRoot("unrelatedModule")
    myFixture.addFileToProject(
      "unrelatedModule/com/example/UnrelatedAction.java",
      //language=JAVA
      "package com.example; public class UnrelatedAction extends com.intellij.openapi.actionSystem.AnAction {}"
    )

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <actions>
              <action class="com.example.LocalAction" id="LocalAction"/>
              <action class="com.example.UnrelatedAction" id="UnrelatedAction"/>
          </actions>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test listener class in unrelated module - no error because registration`() {
    myFixture.addClass("package com.example; public interface MyTopic {}")
    addModuleWithSourceRoot("unrelatedModule")
    myFixture.addFileToProject(
      "unrelatedModule/com/example/UnreachableListener.java",
      //language=JAVA
      "package com.example; public class UnreachableListener {}"
    )

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <applicationListeners>
              <listener class="com.example.UnreachableListener" topic="com.example.MyTopic"/>
          </applicationListeners>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test extensionPoint interface in unrelated module - no error because registration`() {
    myFixture.addClass("package com.example; public interface LocalInterface {}")
    addModuleWithSourceRoot("unrelatedModule")
    myFixture.addFileToProject(
      "unrelatedModule/com/example/UnreachableInterface.java",
      //language=JAVA
      "package com.example; public interface UnreachableInterface {}"
    )

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <extensionPoints>
              <extensionPoint interface="com.example.LocalInterface" name="localEP"/>
              <extensionPoint interface="com.example.UnreachableInterface" name="unreachableEP"/>
          </extensionPoints>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test extensionPoint beanClass in unrelated module - no error because registration`() {
    myFixture.addClass("package com.example; public class LocalBean {}")
    addModuleWithSourceRoot("unrelatedModule")
    myFixture.addFileToProject(
      "unrelatedModule/com/example/UnreachableBean.java",
      //language=JAVA
      "package com.example; public class UnreachableBean {}"
    )

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <extensionPoints>
              <extensionPoint beanClass="com.example.LocalBean" name="localEP"/>
              <extensionPoint beanClass="com.example.UnreachableBean" name="unreachableEP"/>
          </extensionPoints>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test extensionPoint interface in dependency module - no error`() {
    val depModule = addModuleWithSourceRoot("depModule")
    ModuleRootModificationUtil.addDependency(myFixture.module, depModule)
    myFixture.addFileToProject(
      "depModule/com/example/DepInterface.java",
      //language=JAVA
      "package com.example; public interface DepInterface {}"
    )

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <extensionPoints>
              <extensionPoint interface="com.example.DepInterface" name="depEP"/>
          </extensionPoints>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test component implementationClass in unrelated module - no error because registration`() {
    addModuleWithSourceRoot("unrelatedModule")
    myFixture.addFileToProject(
      "unrelatedModule/com/example/UnreachableComponent.java",
      //language=JAVA
      "package com.example; public class UnreachableComponent {}"
    )

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <application-components>
              <component>
                  <implementation-class>com.example.UnreachableComponent</implementation-class>
              </component>
          </application-components>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test listener topic in unrelated module - error`() {
    myFixture.addClass("package com.example; public interface MyTopic {}")
    myFixture.addClass("package com.example; public class MyListener {}")
    addModuleWithSourceRoot("unrelatedModule")
    myFixture.addFileToProject(
      "unrelatedModule/com/example/UnreachableTopic.java",
      //language=JAVA
      "package com.example; public interface UnreachableTopic {}"
    )

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <applicationListeners>
              <listener class="com.example.MyListener" topic="com.example.MyTopic"/>
              <listener class="com.example.MyListener" topic="<error descr="Class 'com.example.UnreachableTopic' (module 'unrelatedModule') is not reachable from module '${myFixture.module.name}' dependencies">com.example.UnreachableTopic</error>"/>
          </applicationListeners>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test component interfaceClass in unrelated module - error`() {
    myFixture.addClass("package com.example; public class LocalComponent {}")
    addModuleWithSourceRoot("unrelatedModule")
    myFixture.addFileToProject(
      "unrelatedModule/com/example/UnreachableInterface.java",
      //language=JAVA
      "package com.example; public interface UnreachableInterface {}"
    )

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <application-components>
              <component>
                  <implementation-class>com.example.LocalComponent</implementation-class>
                  <interface-class><error descr="Class 'com.example.UnreachableInterface' (module 'unrelatedModule') is not reachable from module '${myFixture.module.name}' dependencies">com.example.UnreachableInterface</error></interface-class>
              </component>
          </application-components>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test resource-bundle in unrelated module - error`() {
    addModuleWithSourceRoot("unrelatedModule")
    myFixture.addFileToProject("unrelatedModule/messages/UnreachableBundle.properties", "key=value")

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <resource-bundle><error descr="Bundle 'messages.UnreachableBundle' (module 'unrelatedModule') is not reachable from module '${myFixture.module.name}' dependencies">messages.UnreachableBundle</error></resource-bundle>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test group class in unrelated module - error`() {
    myFixture.addClass("package com.intellij.openapi.actionSystem; public class ActionGroup extends AnAction {}")
    addModuleWithSourceRoot("unrelatedModule")
    myFixture.addFileToProject(
      "unrelatedModule/com/example/UnreachableGroup.java",
      //language=JAVA
      "package com.example; public class UnreachableGroup extends com.intellij.openapi.actionSystem.ActionGroup {}"
    )

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <actions>
              <group class="<error descr="Class 'com.example.UnreachableGroup' (module 'unrelatedModule') is not reachable from module '${myFixture.module.name}' dependencies">com.example.UnreachableGroup</error>" id="UnreachableGroup"/>
          </actions>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test extensionPoint with-implements in unrelated module - error`() {
    myFixture.addClass("package com.example; public class LocalBean { public String impl; }")
    myFixture.addClass("package com.example; public interface LocalInterface {}")
    addModuleWithSourceRoot("unrelatedModule")
    myFixture.addFileToProject(
      "unrelatedModule/com/example/UnreachableWithInterface.java",
      //language=JAVA
      "package com.example; public interface UnreachableWithInterface {}"
    )

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <extensionPoints>
              <extensionPoint beanClass="com.example.LocalBean" name="localEP">
                  <with attribute="impl" implements="com.example.LocalInterface"/>
              </extensionPoint>
              <extensionPoint beanClass="com.example.LocalBean" name="unreachableEP">
                  <with attribute="impl" implements="<error descr="Class 'com.example.UnreachableWithInterface' (module 'unrelatedModule') is not reachable from module '${myFixture.module.name}' dependencies">com.example.UnreachableWithInterface</error>"/>
              </extensionPoint>
          </extensionPoints>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test resource-bundle in dependency module - no error`() {
    val depModule = addModuleWithSourceRoot("depModule")
    ModuleRootModificationUtil.addDependency(myFixture.module, depModule)
    myFixture.addFileToProject("depModule/messages/DepBundle.properties", "key=value")

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <resource-bundle>messages.DepBundle</resource-bundle>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test class in test-dependency module - no error when plugin xml is in test resource root`() {
    myFixture.addClass("package com.example; public class MyListener {}")
    val testDepModule = addModuleWithSourceRoot("testDepModule")
    ModuleRootModificationUtil.addDependency(myFixture.module, testDepModule, DependencyScope.TEST, false)
    myFixture.addFileToProject(
      "testDepModule/com/example/TestDepTopic.java",
      //language=JAVA
      "package com.example; public interface TestDepTopic {}"
    )

    val testResourceDir = myFixture.tempDirFixture.findOrCreateDir("testResources")
    PsiTestUtil.addSourceRoot(myFixture.module, testResourceDir, JavaResourceRootType.TEST_RESOURCE)
    val testedFile = myFixture.addFileToProject(
      "testResources/plugin.xml",
      //language=XML
      """
      <idea-plugin>
          <applicationListeners>
              <listener class="com.example.MyListener" topic="com.example.TestDepTopic"/>
          </applicationListeners>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test class in unrelated module - error when plugin xml is in test resource root`() {
    myFixture.addClass("package com.example; public class MyListener {}")
    addModuleWithSourceRoot("unrelatedModule")
    myFixture.addFileToProject(
      "unrelatedModule/com/example/UnrelatedTopic.java",
      //language=JAVA
      "package com.example; public interface UnrelatedTopic {}"
    )

    val testResourceDir = myFixture.tempDirFixture.findOrCreateDir("testResources")
    PsiTestUtil.addSourceRoot(myFixture.module, testResourceDir, JavaResourceRootType.TEST_RESOURCE)
    val testedFile = myFixture.addFileToProject(
      "testResources/plugin.xml",
      //language=XML
      """
      <idea-plugin>
          <applicationListeners>
              <listener class="com.example.MyListener" topic="<error descr="Class 'com.example.UnrelatedTopic' (module 'unrelatedModule') is not reachable from module '${myFixture.module.name}' dependencies">com.example.UnrelatedTopic</error>"/>
          </applicationListeners>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test class in test-dependency module - error when plugin xml is in production source root`() {
    myFixture.addClass("package com.example; public class MyListener {}")
    val testDepModule = addModuleWithSourceRoot("testDepModule")
    ModuleRootModificationUtil.addDependency(myFixture.module, testDepModule, DependencyScope.TEST, false)
    myFixture.addFileToProject(
      "testDepModule/com/example/TestDepTopic.java",
      //language=JAVA
      "package com.example; public interface TestDepTopic {}"
    )

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <applicationListeners>
              <listener class="com.example.MyListener" topic="<error descr="Class 'com.example.TestDepTopic' (module 'testDepModule') is not reachable from module '${myFixture.module.name}' dependencies">com.example.TestDepTopic</error>"/>
          </applicationListeners>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test quick fix adds module dependency for unreachable class`() {
    myFixture.addClass("package com.intellij.openapi.actionSystem; public class ActionGroup extends AnAction {}")
    val unrelatedModule = addModuleWithSourceRoot("unrelatedModule")
    myFixture.addFileToProject(
      "unrelatedModule/com/example/UnreachableGroup.java",
      //language=JAVA
      "package com.example; public class UnreachableGroup extends com.intellij.openapi.actionSystem.ActionGroup {}"
    )

    myFixture.configureByText(
      "plugin.xml",
      //language=XML
      """
      <idea-plugin>
          <actions>
              <group class="com.example.Unreachable<caret>Group" id="UnreachableGroup"/>
          </actions>
      </idea-plugin>
      """.trimIndent()
    )
    val intention = myFixture.findSingleIntention("Add dependency on module 'unrelatedModule'")
    myFixture.launchAction(intention)
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()

    val dependencies = ModuleRootManager.getInstance(myFixture.module).dependencies
    Assert.assertTrue(
      "JPS module dependency should be added",
      dependencies.any { it.name == unrelatedModule.name })
  }

  fun `test quick fix adds plugin descriptor dependency - v1 descriptor`() {
    val unrelatedModule = addModuleWithSourceRoot("unrelatedModule")
    myFixture.addFileToProject(
      "unrelatedModule/com/example/UnreachableInterface.java",
      //language=JAVA
      "package com.example; public interface UnreachableInterface {}"
    )
    myFixture.addFileToProject(
      "unrelatedModule/META-INF/plugin.xml",
      //language=XML
      """
      <idea-plugin>
          <id>com.example.unrelated</id>
      </idea-plugin>
      """.trimIndent()
    )

    myFixture.configureByText(
      "plugin.xml",
      //language=XML
      """
      <idea-plugin>
          <id>com.example.main</id>
          <applicationListeners>
              <listener class="com.example.UnreachableInterface" topic="com.example.Unreachable<caret>Interface"/>
          </applicationListeners>
      </idea-plugin>
      """.trimIndent()
    )
    val intention = myFixture.findSingleIntention("Add dependency on module 'unrelatedModule'")
    myFixture.launchAction(intention)
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()

    Assert.assertEquals(
      //language=XML
      """
      <idea-plugin>
          <id>com.example.main</id>
          <applicationListeners>
              <listener class="com.example.UnreachableInterface" topic="com.example.UnreachableInterface"/>
          </applicationListeners>
          <depends>com.example.unrelated</depends>
      </idea-plugin>
      """.trimIndent(),
      myFixture.file.text
    )
    val dependencies = ModuleRootManager.getInstance(myFixture.module).dependencies
    Assert.assertTrue(
      "JPS module dependency should be added",
      dependencies.any { it.name == unrelatedModule.name })
  }

  fun `test quick fix adds plugin descriptor dependency - v2 descriptor`() {
    val unrelatedModule = addModuleWithSourceRoot("unrelatedModule")
    myFixture.addFileToProject(
      "unrelatedModule/com/example/UnreachableInterface.java",
      //language=JAVA
      "package com.example; public interface UnreachableInterface {}"
    )
    myFixture.addFileToProject(
      "unrelatedModule/META-INF/plugin.xml",
      //language=XML
      """
      <idea-plugin>
          <id>com.example.unrelated</id>
      </idea-plugin>
      """.trimIndent()
    )

    myFixture.configureByText(
      "plugin.xml",
      //language=XML
      """
      <idea-plugin>
          <id>com.example.main</id>
          <dependencies>
              <plugin id="com.intellij.modules.platform"/>
          </dependencies>
          <applicationListeners>
              <listener class="com.example.UnreachableInterface" topic="com.example.Unreachable<caret>Interface"/>
          </applicationListeners>
      </idea-plugin>
      """.trimIndent()
    )
    val intention = myFixture.findSingleIntention("Add dependency on module 'unrelatedModule'")
    myFixture.launchAction(intention)
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()

    Assert.assertEquals(
      //language=XML
      """
      <idea-plugin>
          <id>com.example.main</id>
          <dependencies>
              <plugin id="com.intellij.modules.platform"/>
              <plugin id="com.example.unrelated"/>
          </dependencies>
          <applicationListeners>
              <listener class="com.example.UnreachableInterface" topic="com.example.UnreachableInterface"/>
          </applicationListeners>
      </idea-plugin>
      """.trimIndent(),
      myFixture.file.text
    )
    val dependencies = ModuleRootManager.getInstance(myFixture.module).dependencies
    Assert.assertTrue(
      "JPS module dependency should be added",
      dependencies.any { it.name == unrelatedModule.name })
  }

  fun `test quick fix adds plugin descriptor dependency - v2 descriptor content module`() {
    val unrelatedModule = addModuleWithSourceRoot("unrelatedModule")
    val resourceRoot = myFixture.tempDirFixture.findOrCreateDir("unrelatedModule/resources")
    PsiTestUtil.addSourceRoot(unrelatedModule, resourceRoot, JavaResourceRootType.RESOURCE)
    myFixture.addFileToProject(
      "unrelatedModule/com/example/UnreachableInterface.java",
      //language=JAVA
      "package com.example; public interface UnreachableInterface {}"
    )
    myFixture.addFileToProject(
      "unrelatedModule/resources/unrelatedModule.xml",
      //language=XML
      """
      <idea-plugin>
      </idea-plugin>
      """.trimIndent()
    )

    myFixture.configureByText(
      "plugin.xml",
      //language=XML
      """
      <idea-plugin>
          <id>com.example.main</id>
          <dependencies>
              <plugin id="com.intellij.modules.platform"/>
          </dependencies>
          <applicationListeners>
              <listener class="com.example.UnreachableInterface" topic="com.example.Unreachable<caret>Interface"/>
          </applicationListeners>
      </idea-plugin>
      """.trimIndent()
    )
    val intention = myFixture.findSingleIntention("Add dependency on module 'unrelatedModule'")
    myFixture.launchAction(intention)
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()

    Assert.assertEquals(
      //language=XML
      """
      <idea-plugin>
          <id>com.example.main</id>
          <dependencies>
              <plugin id="com.intellij.modules.platform"/>
              <module name="unrelatedModule"/>
          </dependencies>
          <applicationListeners>
              <listener class="com.example.UnreachableInterface" topic="com.example.UnreachableInterface"/>
          </applicationListeners>
      </idea-plugin>
      """.trimIndent(),
      myFixture.file.text
    )
    val dependencies = ModuleRootManager.getInstance(myFixture.module).dependencies
    Assert.assertTrue(
      "JPS module dependency should be added",
      dependencies.any { it.name == unrelatedModule.name })
  }

  fun `test action or group id references in unrelated module - error`() {
    val unrelatedModule = addModuleWithSourceRoot("unrelatedModule")
    val resourceRoot = myFixture.tempDirFixture.findOrCreateDir("unrelatedModule/resources")
    PsiTestUtil.addSourceRoot(unrelatedModule, resourceRoot, JavaResourceRootType.RESOURCE)
    myFixture.addFileToProject(
      "unrelatedModule/resources/META-INF/plugin.xml",
      //language=XML
      """
      <idea-plugin>
          <actions>
              <group id="UnrelatedGroup"/>
              <action class="com.intellij.openapi.actionSystem.AnAction" id="UnrelatedAction"/>
          </actions>
      </idea-plugin>
      """.trimIndent()
    )

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <actions>
              <action class="com.intellij.openapi.actionSystem.AnAction" id="MyAction">
                  <add-to-group group-id="<error descr="Action or group 'UnrelatedGroup' (module 'unrelatedModule') is not reachable from module '${myFixture.module.name}' dependencies">UnrelatedGroup</error>" relative-to-action="<error descr="Action or group 'UnrelatedAction' (module 'unrelatedModule') is not reachable from module '${myFixture.module.name}' dependencies">UnrelatedAction</error>" anchor="after"/>
              </action>
              <reference ref="<error descr="Action or group 'UnrelatedAction' (module 'unrelatedModule') is not reachable from module '${myFixture.module.name}' dependencies">UnrelatedAction</error>"/>
          </actions>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test action or group id references in dependency module - no error`() {
    val depModule = addModuleWithSourceRoot("depModule")
    val resourceRoot = myFixture.tempDirFixture.findOrCreateDir("depModule/resources")
    PsiTestUtil.addSourceRoot(depModule, resourceRoot, JavaResourceRootType.RESOURCE)
    ModuleRootModificationUtil.addDependency(myFixture.module, depModule)
    myFixture.addFileToProject(
      "depModule/resources/META-INF/plugin.xml",
      //language=XML
      """
      <idea-plugin>
          <actions>
              <group id="DepGroup"/>
              <action class="com.intellij.openapi.actionSystem.AnAction" id="DepAction"/>
          </actions>
      </idea-plugin>
      """.trimIndent()
    )

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <actions>
              <action class="com.intellij.openapi.actionSystem.AnAction" id="MyAction">
                  <add-to-group group-id="DepGroup" relative-to-action="DepAction" anchor="after"/>
              </action>
              <reference ref="DepAction"/>
          </actions>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test extension point in unrelated module - error`() {
    val unrelatedModule = addModuleWithSourceRoot("unrelatedModule")
    val resourceRoot = myFixture.tempDirFixture.findOrCreateDir("unrelatedModule/resources")
    PsiTestUtil.addSourceRoot(unrelatedModule, resourceRoot, JavaResourceRootType.RESOURCE)
    myFixture.addFileToProject(
      "unrelatedModule/resources/META-INF/plugin.xml",
      //language=XML
      """
      <idea-plugin>
          <extensionPoints>
              <extensionPoint qualifiedName="com.example.myTestEp" interface="java.lang.Runnable" dynamic="true"/>
          </extensionPoints>
      </idea-plugin>
      """.trimIndent()
    )
    myFixture.addClass("package com.example; public class MyImpl implements Runnable { public void run() {} }")

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <extensions defaultExtensionNs="com.example">
              <<error descr="Extension point 'com.example.myTestEp' (module 'unrelatedModule') is not reachable from module '${myFixture.module.name}' dependencies">myTestEp</error> implementation="com.example.MyImpl"/>
          </extensions>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test extension point in dependency module - no error`() {
    val depModule = addModuleWithSourceRoot("depModule")
    val resourceRoot = myFixture.tempDirFixture.findOrCreateDir("depModule/resources")
    PsiTestUtil.addSourceRoot(depModule, resourceRoot, JavaResourceRootType.RESOURCE)
    ModuleRootModificationUtil.addDependency(myFixture.module, depModule)
    myFixture.addFileToProject(
      "depModule/resources/META-INF/plugin.xml",
      //language=XML
      """
      <idea-plugin>
          <extensionPoints>
              <extensionPoint qualifiedName="com.example.myTestEp" interface="java.lang.Runnable" dynamic="true"/>
          </extensionPoints>
      </idea-plugin>
      """.trimIndent()
    )
    myFixture.addClass("package com.example; public class MyImpl implements Runnable { public void run() {} }")

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <extensions defaultExtensionNs="com.example">
              <myTestEp implementation="com.example.MyImpl"/>
          </extensions>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test references to module in same plugin module set - no error`() {
    val siblingModule = addModuleWithActionsXml("siblingModule", "Sibling")
    declarePluginModuleSets(listOf(myFixture.module.name, siblingModule.name))

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <actions>
              <action class="com.intellij.openapi.actionSystem.AnAction" id="MyAction">
                  <add-to-group group-id="SiblingGroup" relative-to-action="SiblingAction" anchor="after"/>
              </action>
              <reference ref="SiblingAction"/>
          </actions>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test references to module in another plugin module set - error`() {
    val otherPluginModule = addModuleWithActionsXml("otherPluginModule", "Other")
    declarePluginModuleSets(listOf(myFixture.module.name), listOf(otherPluginModule.name))

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <actions>
              <reference ref="<error descr="Action or group 'OtherAction' (module 'otherPluginModule') is not reachable from module '${myFixture.module.name}' dependencies">OtherAction</error>"/>
          </actions>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test class in test source root of same module - error`() {
    addTestOnlyTopic()

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <applicationListeners>
              <listener class="com.example.MyListener" topic="<error descr="Class 'com.example.TestOnlyTopic' (module '${myFixture.module.name}') is not reachable from module '${myFixture.module.name}' dependencies">com.example.TestOnlyTopic</error>"/>
          </applicationListeners>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test class in own test source root while module is in a plugin module set - error`() {
    addTestOnlyTopic()
    declarePluginModuleSets(listOf(myFixture.module.name, addModuleWithSourceRoot("siblingModule").name))

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <applicationListeners>
              <listener class="com.example.MyListener" topic="<error descr="Class 'com.example.TestOnlyTopic' (module '${myFixture.module.name}') is not reachable from module '${myFixture.module.name}' dependencies">com.example.TestOnlyTopic</error>"/>
          </applicationListeners>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test class in test source root of sibling module in same plugin module set - error`() {
    myFixture.addClass("package com.example; public class MyListener {}")
    val siblingModule = addModuleWithSourceRoot("siblingModule")
    PsiTestUtil.addSourceRoot(siblingModule, myFixture.tempDirFixture.findOrCreateDir("siblingModule/testSrc"), true)
    myFixture.addFileToProject(
      "siblingModule/testSrc/com/example/SiblingTestOnlyTopic.java",
      //language=JAVA
      "package com.example; public interface SiblingTestOnlyTopic {}"
    )
    declarePluginModuleSets(listOf(myFixture.module.name, siblingModule.name))

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <applicationListeners>
              <listener class="com.example.MyListener" topic="<error descr="Class 'com.example.SiblingTestOnlyTopic' (module 'siblingModule') is not reachable from module '${myFixture.module.name}' dependencies">com.example.SiblingTestOnlyTopic</error>"/>
          </applicationListeners>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test class in production source root of sibling module in same plugin module set - error because of separate classloader`() {
    myFixture.addClass("package com.example; public class MyListener {}")
    val siblingModule = addModuleWithSourceRoot("siblingModule")
    myFixture.addFileToProject(
      "siblingModule/com/example/SiblingTopic.java",
      //language=JAVA
      "package com.example; public interface SiblingTopic {}"
    )
    declarePluginModuleSets(listOf(myFixture.module.name, siblingModule.name))

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <applicationListeners>
              <listener class="com.example.MyListener" topic="<error descr="Class 'com.example.SiblingTopic' (module 'siblingModule') is not reachable from module '${myFixture.module.name}' dependencies">com.example.SiblingTopic</error>"/>
          </applicationListeners>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test extension point in sibling module in same plugin module set - no error`() {
    val siblingModule = addModuleWithSourceRoot("siblingModule")
    val resourceRoot = myFixture.tempDirFixture.findOrCreateDir("siblingModule/resources")
    PsiTestUtil.addSourceRoot(siblingModule, resourceRoot, JavaResourceRootType.RESOURCE)
    myFixture.addFileToProject(
      "siblingModule/resources/META-INF/plugin.xml",
      //language=XML
      """
      <idea-plugin>
          <extensionPoints>
              <extensionPoint qualifiedName="com.example.myTestEp" interface="java.lang.Runnable" dynamic="true"/>
          </extensionPoints>
      </idea-plugin>
      """.trimIndent()
    )
    myFixture.addClass("package com.example; public class MyImpl implements Runnable { public void run() {} }")
    declarePluginModuleSets(listOf(myFixture.module.name, siblingModule.name))

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <extensions defaultExtensionNs="com.example">
              <myTestEp implementation="com.example.MyImpl"/>
          </extensions>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test resource-bundle in sibling module in same plugin module set - error because of separate classloader`() {
    val siblingModule = addModuleWithSourceRoot("siblingModule")
    myFixture.addFileToProject("siblingModule/messages/SiblingBundle.properties", "key=value")
    declarePluginModuleSets(listOf(myFixture.module.name, siblingModule.name))

    val testedFile = addPluginXml(
      """
      <idea-plugin>
          <resource-bundle><error descr="Bundle 'messages.SiblingBundle' (module 'siblingModule') is not reachable from module '${myFixture.module.name}' dependencies">messages.SiblingBundle</error></resource-bundle>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test class in production source root of sibling module in same plugin module set - error when plugin xml is in test resource root`() {
    myFixture.addClass("package com.example; public class MyListener {}")
    val siblingModule = addModuleWithSourceRoot("siblingModule")
    myFixture.addFileToProject(
      "siblingModule/com/example/SiblingTopic.java",
      //language=JAVA
      "package com.example; public interface SiblingTopic {}"
    )
    declarePluginModuleSets(listOf(myFixture.module.name, siblingModule.name))

    val testResourceDir = myFixture.tempDirFixture.findOrCreateDir("testResources")
    PsiTestUtil.addSourceRoot(myFixture.module, testResourceDir, JavaResourceRootType.TEST_RESOURCE)
    val testedFile = myFixture.addFileToProject(
      "testResources/plugin.xml",
      //language=XML
      """
      <idea-plugin>
          <applicationListeners>
              <listener class="com.example.MyListener" topic="<error descr="Class 'com.example.SiblingTopic' (module 'siblingModule') is not reachable from module '${myFixture.module.name}' dependencies">com.example.SiblingTopic</error>"/>
          </applicationListeners>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test class in test source root of sibling module - error even when plugin xml is in test resource root`() {
    myFixture.addClass("package com.example; public class MyListener {}")
    val siblingModule = addModuleWithSourceRoot("siblingModule")
    PsiTestUtil.addSourceRoot(siblingModule, myFixture.tempDirFixture.findOrCreateDir("siblingModule/testSrc"), true)
    myFixture.addFileToProject(
      "siblingModule/testSrc/com/example/SiblingTestOnlyTopic.java",
      //language=JAVA
      "package com.example; public interface SiblingTestOnlyTopic {}"
    )
    declarePluginModuleSets(listOf(myFixture.module.name, siblingModule.name))

    val testResourceDir = myFixture.tempDirFixture.findOrCreateDir("testResources")
    PsiTestUtil.addSourceRoot(myFixture.module, testResourceDir, JavaResourceRootType.TEST_RESOURCE)
    val testedFile = myFixture.addFileToProject(
      "testResources/plugin.xml",
      //language=XML
      """
      <idea-plugin>
          <applicationListeners>
              <listener class="com.example.MyListener" topic="<error descr="Class 'com.example.SiblingTestOnlyTopic' (module 'siblingModule') is not reachable from module '${myFixture.module.name}' dependencies">com.example.SiblingTestOnlyTopic</error>"/>
          </applicationListeners>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test no add-dependency quick fix for a class in test source root of another module`() {
    myFixture.addClass("package com.example; public class MyListener {}")
    val otherModule = addModuleWithSourceRoot("otherModule")
    PsiTestUtil.addSourceRoot(otherModule, myFixture.tempDirFixture.findOrCreateDir("otherModule/testSrc"), true)
    myFixture.addFileToProject(
      "otherModule/testSrc/com/example/OtherTestOnlyTopic.java",
      //language=JAVA
      "package com.example; public interface OtherTestOnlyTopic {}"
    )

    myFixture.configureByText(
      "plugin.xml",
      //language=XML
      """
      <idea-plugin>
          <applicationListeners>
              <listener class="com.example.MyListener" topic="com.example.OtherTestOnly<caret>Topic"/>
          </applicationListeners>
      </idea-plugin>
      """.trimIndent()
    )
    val intentions = myFixture.filterAvailableIntentions("Add dependency on module")
    Assert.assertTrue("test output stays off a production classpath, but got: ${intentions.map { it.text }}", intentions.isEmpty())
  }

  fun `test quick fix adds test dependency for a class in test source root of another module when plugin xml is in test resource root`() {
    myFixture.addClass("package com.example; public class MyListener {}")
    val otherModule = addModuleWithSourceRoot("otherModule")
    PsiTestUtil.addSourceRoot(otherModule, myFixture.tempDirFixture.findOrCreateDir("otherModule/testSrc"), true)
    myFixture.addFileToProject(
      "otherModule/testSrc/com/example/OtherTestOnlyTopic.java",
      //language=JAVA
      "package com.example; public interface OtherTestOnlyTopic {}"
    )

    val testResourceDir = myFixture.tempDirFixture.findOrCreateDir("testResources")
    PsiTestUtil.addSourceRoot(myFixture.module, testResourceDir, JavaResourceRootType.TEST_RESOURCE)
    val testedFile = myFixture.addFileToProject(
      "testResources/plugin.xml",
      //language=XML
      """
      <idea-plugin>
          <applicationListeners>
              <listener class="com.example.MyListener" topic="com.example.OtherTestOnlyTopic"/>
          </applicationListeners>
      </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(testedFile.virtualFile)
    myFixture.editor.caretModel.moveToOffset(testedFile.text.indexOf("com.example.OtherTestOnlyTopic"))

    val intention = myFixture.findSingleIntention("Add test dependency on module 'otherModule'")
    myFixture.launchAction(intention)
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()

    val dependency = ModuleRootManager.getInstance(myFixture.module).orderEntries
      .filterIsInstance<ModuleOrderEntry>()
      .single { it.moduleName == otherModule.name }
    Assert.assertEquals("only a test dependency puts test output on a test classpath", DependencyScope.TEST, dependency.scope)
  }

  fun `test quick fix adds test dependency for a class in production source root of another module when plugin xml is in test resource root`() {
    myFixture.addClass("package com.example; public class MyListener {}")
    val otherModule = addModuleWithSourceRoot("otherModule")
    myFixture.addFileToProject(
      "otherModule/com/example/OtherTopic.java",
      //language=JAVA
      "package com.example; public interface OtherTopic {}"
    )

    val testResourceDir = myFixture.tempDirFixture.findOrCreateDir("testResources")
    PsiTestUtil.addSourceRoot(myFixture.module, testResourceDir, JavaResourceRootType.TEST_RESOURCE)
    val testedFile = myFixture.addFileToProject(
      "testResources/plugin.xml",
      //language=XML
      """
      <idea-plugin>
          <applicationListeners>
              <listener class="com.example.MyListener" topic="com.example.OtherTopic"/>
          </applicationListeners>
      </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(testedFile.virtualFile)
    myFixture.editor.caretModel.moveToOffset(testedFile.text.indexOf("com.example.OtherTopic"))

    val intention = myFixture.findSingleIntention("Add test dependency on module 'otherModule'")
    myFixture.launchAction(intention)
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()

    val dependency = ModuleRootManager.getInstance(myFixture.module).orderEntries
      .filterIsInstance<ModuleOrderEntry>()
      .single { it.moduleName == otherModule.name }
    Assert.assertEquals("a test descriptor needs its target only on the test classpath", DependencyScope.TEST, dependency.scope)
  }

  fun `test no self-dependency quick fix for a class in test source root of same module`() {
    addTestOnlyTopic()

    myFixture.configureByText(
      "plugin.xml",
      //language=XML
      """
      <idea-plugin>
          <applicationListeners>
              <listener class="com.example.MyListener" topic="com.example.TestOnly<caret>Topic"/>
          </applicationListeners>
      </idea-plugin>
      """.trimIndent()
    )
    val intentions = myFixture.filterAvailableIntentions("Add dependency on module")
    Assert.assertTrue("a module cannot depend on itself, but got: ${intentions.map { it.text }}", intentions.isEmpty())
  }

  private fun addTestOnlyTopic() {
    myFixture.addClass("package com.example; public class MyListener {}")
    val testSourceDir = myFixture.tempDirFixture.findOrCreateDir("testSrc")
    PsiTestUtil.addSourceRoot(myFixture.module, testSourceDir, true)
    myFixture.addFileToProject(
      "testSrc/com/example/TestOnlyTopic.java",
      //language=JAVA
      "package com.example; public interface TestOnlyTopic {}"
    )
  }

  private fun addModuleWithActionsXml(moduleName: String, idPrefix: String): Module {
    val module = addModuleWithSourceRoot(moduleName)
    val resourceRoot = myFixture.tempDirFixture.findOrCreateDir("$moduleName/resources")
    PsiTestUtil.addSourceRoot(module, resourceRoot, JavaResourceRootType.RESOURCE)
    myFixture.addFileToProject(
      "$moduleName/resources/META-INF/plugin.xml",
      //language=XML
      """
      <idea-plugin>
          <actions>
              <group id="${idPrefix}Group"/>
              <action class="com.intellij.openapi.actionSystem.AnAction" id="${idPrefix}Action"/>
          </actions>
      </idea-plugin>
      """.trimIndent()
    )
    return module
  }

  /**
   * Plugin module sets are configured on [PluginXmlRegistrationCheckInspection], so it has to be present in the profile for
   * [PluginXmlReferencesModuleReachabilityInspection] to see them.
   */
  private fun declarePluginModuleSets(vararg moduleSets: List<String>) {
    val registrationCheck = PluginXmlRegistrationCheckInspection()
    moduleSets.forEach { moduleNames ->
      registrationCheck.pluginsModules.add(PluginXmlRegistrationCheckInspection.PluginModuleSet().apply {
        modules = LinkedHashSet(moduleNames)
      })
    }
    myFixture.enableInspections(registrationCheck)
  }

  private fun addPluginXml(@Language("XML") content: String): PsiFile {
    return myFixture.addFileToProject("plugin.xml", content)
  }

  private fun testHighlighting(testedFile: PsiFile) {
    myFixture.testHighlighting(false, false, false, testedFile.virtualFile)
  }

  private fun addModuleWithSourceRoot(name: String): Module {
    val dir = myFixture.tempDirFixture.findOrCreateDir(name)
    val module = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), name, dir)
    PsiTestUtil.addSourceRoot(module, dir)
    return module
  }
}
