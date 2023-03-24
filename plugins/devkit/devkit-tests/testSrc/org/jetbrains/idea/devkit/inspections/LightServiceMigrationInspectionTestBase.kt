// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.intention.IntentionActionBean
import com.intellij.codeInspection.LocalInspectionEP
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceContributorEP
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.IncorrectOperationException
import com.intellij.util.PathUtil
import java.nio.file.Paths

abstract class LightServiceMigrationInspectionTestBase : JavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    //language=java
    myFixture.addClass("""
      package com.intellij.openapi.components;
      public interface PersistentStateComponent<T> { }
    """)
    myFixture.enableInspections(LightServiceMigrationXMLInspection::class.java,
                                LightServiceMigrationCodeInspection::class.java)
  }

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    moduleBuilder.addLibrary("platform-core", PathUtil.getJarPathForClass(Project::class.java))
    moduleBuilder.addLibrary("platform-core-impl", PathUtil.getJarPathForClass(PsiReferenceContributorEP::class.java))
    moduleBuilder.addLibrary("platform-analysis", PathUtil.getJarPathForClass(IntentionActionBean::class.java))
    moduleBuilder.addLibrary("platform-util-base", PathUtil.getJarPathForClass(IncorrectOperationException::class.java))
    moduleBuilder.addLibrary("platform-resources", Paths.get(PathUtil.getJarPathForClass(LocalInspectionEP::class.java))
      .resolveSibling("intellij.platform.resources").toString())
  }

}