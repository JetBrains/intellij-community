// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests.targets.java

import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.execution.testframework.TestSearchScope
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.CompilerProjectExtension
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PlatformTestUtil
import org.junit.Test

abstract class JavaTargetMultiModuleTestBase(executionMode: ExecutionMode) : CommonJavaTargetTestBase(executionMode) {
  override fun getTestAppPath(): String = "${PlatformTestUtil.getCommunityPath()}/platform/remote-servers/target-integration-tests/multiModuleTargetApp"

  override fun setUpModule() {
    super.setUpModule()

    val secondModule = createModule("secondModule")

    ApplicationManager.getApplication().runWriteAction(Runnable {
      IdeaTestUtil.setModuleLanguageLevel(secondModule, LanguageLevel.JDK_1_8)
    })

    val contentRoot = LocalFileSystem.getInstance().findFileByPath(testAppPath)!!
    initializeSampleModule(module, contentRoot)
    initializeSampleModule(secondModule, contentRoot.findChild("secondModule")!!)
  }

  override fun setUp() {
    super.setUp()

    // Recompile the test project to restore `com.intellij.openapi.roots.impl.CompilerProjectExtensionImpl.myCompilerOutput` otherwise the
    // classes of `secondModule` is out of scope
    if (CompilerProjectExtension.getInstance(project)?.compilerOutput == null) {
      compileProject()
    }
  }

  @Test
  fun `test junit tests - run all in two modules`() {
    val runConfiguration = createJUnitConfiguration(JUnitConfiguration.TEST_PACKAGE)

    @Suppress("SpellCheckingInspection")
    doTestJUnitRunConfiguration(runConfiguration = runConfiguration,
                                expectedTestsResultExported = "<testrun name=\"JUnit tests Run Configuration\">\n" +
                                                              "  <count name=\"total\" value=\"3\" />\n" +
                                                              "  <count name=\"failed\" value=\"1\" />\n" +
                                                              "  <count name=\"passed\" value=\"2\" />\n" +
                                                              "  <root name=\"&lt;default package&gt;\" location=\"java:suite://&lt;default package&gt;\" />\n" +
                                                              "  <suite locationUrl=\"java:suite://AlsoTest\" name=\"AlsoTest\" status=\"failed\">\n" +
                                                              "    <test locationUrl=\"java:test://AlsoTest/testShouldFail\" name=\"testShouldFail()\" metainfo=\"\" status=\"failed\">\n" +
                                                              "      <diff actual=\"5\" expected=\"4\" />\n" +
                                                              "      <output type=\"stdout\">Debugger: testShouldFail() reached</output>\n" +
                                                              "      <output type=\"stderr\">org.opentest4j.AssertionFailedError: \n" +
                                                              "\tat org.junit.jupiter.api.AssertionUtils.fail(AssertionUtils.java:54)\n" +
                                                              "\tat org.junit.jupiter.api.AssertEquals.failNotEqual(AssertEquals.java:195)\n" +
                                                              "\tat org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:152)\n" +
                                                              "\tat org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:147)\n" +
                                                              "\tat org.junit.jupiter.api.Assertions.assertEquals(Assertions.java:326)\n" +
                                                              "\tat AlsoTest.testShouldFail(AlsoTest.java:12)\n" +
                                                              "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n" +
                                                              "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(</output>\n" +
                                                              "    </test>\n" +
                                                              "  </suite>\n" +
                                                              "  <suite locationUrl=\"java:suite://SecondModuleTest\" name=\"SecondModuleTest\" status=\"passed\">\n" +
                                                              "    <test locationUrl=\"java:test://SecondModuleTest/testSomething\" name=\"testSomething()\" metainfo=\"\" status=\"passed\" />\n" +
                                                              "  </suite>\n" +
                                                              "  <suite locationUrl=\"java:suite://SomeTest\" name=\"SomeTest\" status=\"passed\">\n" +
                                                              "    <test locationUrl=\"java:test://SomeTest/testSomething\" name=\"testSomething()\" metainfo=\"\" status=\"passed\">\n" +
                                                              "      <output type=\"stdout\">Debugger: testSomething() reached</output>\n" +
                                                              "    </test>\n" +
                                                              "  </suite>\n" +
                                                              "</testrun>")
  }

  private fun createJUnitConfiguration(testObject: String) = JUnitConfiguration("JUnit tests Run Configuration", project).also { conf ->
    conf.defaultTargetName = targetName
    conf.persistentData.scope = TestSearchScope.WHOLE_PROJECT
    conf.persistentData.TEST_OBJECT = testObject
    conf.persistentData.workingDirectory = testAppPath
  }
}
