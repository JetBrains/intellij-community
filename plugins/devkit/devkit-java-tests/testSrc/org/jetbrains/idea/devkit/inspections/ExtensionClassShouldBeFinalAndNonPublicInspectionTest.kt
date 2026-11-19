// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.quickfix.extensionClassShouldBeFinalAndNonPublicFixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/extensionClassShouldBeFinalAndNonPublic")
@TestApplication
internal class ExtensionClassShouldBeFinalAndNonPublicInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val extensionClass by extensionClassShouldBeFinalAndNonPublicFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/extensionClassShouldBeFinalAndNonPublic/")
  private val myFixture get() = extensionClass.fixture

  @BeforeEach
  fun setUp() {
    myFixture.configureByFile("plugin.xml")
  }

  @Test
  fun testMakeFinal() {
    extensionClass.doTest("Make 'MyInspection' final")
  }

  @Test
  fun testMakeNotPublic() {
    extensionClass.doTest("Make 'MakeNotPublic' not public")
  }

  @Test
  fun testMakeNotPublicService() {
    extensionClass.addServiceDescriptorClass()
    extensionClass.doTest("Make 'MakeNotPublicService' not public")
  }

  @Test
  fun testFinalPackagePrivateExtensionClass() {
    extensionClass.doTest()
  }

  @Test
  fun testHasInheritor() {
    extensionClass.doTest()
  }

  @Test
  fun testVisibleForTestingAnnotation() {
    extensionClass.doTest()
  }

  @Test
  fun testProtectedMembers() {
    extensionClass.doTest("Make 'MyInspection' final")
  }

  @Test
  fun testFinalMethod() {
    extensionClass.doTest("Make 'MyInspection' final")
  }
}