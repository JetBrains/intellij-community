// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection.naming

import com.intellij.junit.testFramework.addJUnit3Library
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.codeInspection.naming.NewGroovyClassNamingConventionInspection

class GroovyJUnit3NamingConventionInspectionTest : JvmInspectionTestBase() {
  override val inspection by lazy {
    NewGroovyClassNamingConventionInspection().apply {
      setEnabled(true, TestClassNamingConvention.TEST_CLASS_NAMING_CONVENTION_SHORT_NAME)
      setEnabled(true, AbstractTestClassNamingConvention.ABSTRACT_TEST_CLASS_NAMING_CONVENTION_SHORT_NAME)
    }
  }

  private class JUnitProjectDescriptor(languageLevel: LanguageLevel) : ProjectDescriptor(languageLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      model.addJUnit3Library()
    }
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = JUnitProjectDescriptor(LanguageLevel.HIGHEST)

  fun testTestCaseConvention() {
    myFixture.testHighlighting(JvmLanguage.GROOVY, """
      import junit.framework.TestCase

      class SpecialGoodTest extends TestCase {
        class MyVeryInner extends SpecialGoodTest {}
      }
      class <warning descr="Test class name 'SpecialBad' doesn't match regex '[A-Z][A-Za-z\d]*Test(s|Case)?|Test[A-Z][A-Za-z\d]*|IT(.*)|(.*)IT(Case)?'">SpecialBad</warning> extends TestCase { }
      class TestInTheBeginning extends TestCase { }
      class WithTests extends TestCase { }
      class WithTestCase extends TestCase { }
      abstract class <warning descr="Abstract test class name 'SpecialAbstract' doesn't match regex '[A-Z][A-Za-z\d]*TestCase'">SpecialAbstract</warning> extends TestCase { }
      abstract class SpecialAbstractTestCase extends TestCase { }
    """.trimIndent())
  }

}