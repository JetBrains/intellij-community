// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.ModificationTracker
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.kotlin.KotlinTester
import com.intellij.util.PathUtil
import com.intellij.util.ThreeState
import com.intellij.util.xmlb.annotations.OptionTag

abstract class PersistentStatePropertyNotSerializedInspectionTestBase : JavaCodeInsightFixtureTestCase() {

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    // The test uses the real classes, because a state class binds a stored property through a delegate of `BaseState`.
    moduleBuilder.addLibrary("platform-projectModel-api", PathUtil.getJarPathForClass(PersistentStateComponent::class.java))
    moduleBuilder.addLibrary("platform-util", PathUtil.getJarPathForClass(OptionTag::class.java))
    moduleBuilder.addLibrary("platform-util-base", PathUtil.getJarPathForClass(Logger::class.java))
    // The compiler resolves every parameter type of `@Storage`, so the test needs the real `ThreeState`.
    moduleBuilder.addLibrary("platform-util-multiplatform", PathUtil.getJarPathForClass(ThreeState::class.java))
    moduleBuilder.addLibrary("platform-core-api", PathUtil.getJarPathForClass(ModificationTracker::class.java))
    // The inspection skips a member that holds a dependency of the component, so the test needs the real `Project`.
    moduleBuilder.addLibrary("platform-project-api", PathUtil.getJarPathForClass(Project::class.java))
    moduleBuilder.addLibrary("platform-extensions", PathUtil.getJarPathForClass(ComponentManager::class.java))
  }

  override fun setUp() {
    super.setUp()
    ModuleRootModificationUtil.updateModel(module) {
      KotlinTester.configureKotlinStdLib(it)
      DefaultLightProjectDescriptor.addJetBrainsAnnotations(it)
    }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    myFixture.enableInspections(PersistentStatePropertyNotSerializedInspection())
  }
}
