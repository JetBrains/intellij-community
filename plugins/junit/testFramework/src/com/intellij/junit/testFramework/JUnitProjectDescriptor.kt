// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit.testFramework

import com.intellij.junit.testFramework.MavenTestLib.HAMCREST
import com.intellij.junit.testFramework.MavenTestLib.JUNIT3
import com.intellij.junit.testFramework.MavenTestLib.JUNIT4
import com.intellij.junit.testFramework.MavenTestLib.JUNIT5
import com.intellij.junit.testFramework.MavenTestLib.JUNIT5_7_0
import com.intellij.junit.testFramework.MavenTestLib.JUNIT6
import com.intellij.junit.testFramework.MavenTestLib.PIONEER
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.AcceptedLanguageLevelsSettings
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor

open class JUnitProjectDescriptor(
  private val languageLevel: LanguageLevel,
  private val libraries: Set<MavenTestLib>,
) : DefaultLightProjectDescriptor() {

  constructor(languageLevel: LanguageLevel, vararg libraries: MavenTestLib = arrayOf(JUNIT3, JUNIT4, JUNIT5))
    : this(languageLevel, libraries.toSet())

  @Throws(Exception::class)
  override fun setUpProject(project: Project, handler: SetupHandler) {
    if (languageLevel.isPreview || languageLevel == LanguageLevel.JDK_X) {
      AcceptedLanguageLevelsSettings.allowLevel(project, languageLevel)
    }
    super.setUpProject(project, handler)
  }

  override fun getSdk(): Sdk {
    return IdeaTestUtil.getMockJdk(languageLevel.toJavaVersion())
  }

  override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
    model.getModuleExtension(LanguageLevelModuleExtension::class.java).setLanguageLevel(languageLevel)
    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
      addJetBrainsAnnotationsWithTypeUse(model)
    }
    else {
      addJetBrainsAnnotations(model)
    }

    libraries.forEach { lib ->
      when (lib) {
        JUNIT3 -> model.addJUnit3Library()
        JUNIT4 -> model.addJUnit4Library()
        JUNIT5_7_0 -> model.addJUnit5Library(JUNIT5_7_0.version)
        JUNIT5 -> model.addJUnit5Library(JUNIT5.version)
        JUNIT6 -> model.addJUnit5Library(JUNIT6.version)
        PIONEER -> model.addJUnitPioneerLibrary(PIONEER.version)
        HAMCREST -> model.addHamcrestLibrary()
      }
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as JUnitProjectDescriptor

    return languageLevel == other.languageLevel && libraries == other.libraries
  }

  override fun hashCode(): Int = 31 * languageLevel.hashCode() + libraries.hashCode()
}
