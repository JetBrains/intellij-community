// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.kotlin.KotlinTester
import com.intellij.util.Alarm
import com.intellij.util.PathUtil
import org.jetbrains.idea.devkit.inspections.quickfix.DevKitInspectionFixTestBase

abstract class PotentialDeadlockInServiceInitializationInspectionTestBase : DevKitInspectionFixTestBase() {

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    // too many classes to add manually via addClass, so using the slower libraries approach:
    moduleBuilder.addLibrary("core-api", PathUtil.getJarPathForClass(Application::class.java))
    moduleBuilder.addLibrary("projectModel-api", PathUtil.getJarPathForClass(PersistentStateComponent::class.java))
    moduleBuilder.addLibrary("platform-ide-core", PathUtil.getJarPathForClass(Alarm::class.java))
    moduleBuilder.addLibrary("platform-util", PathUtil.getJarPathForClass(Disposable::class.java))
    moduleBuilder.addLibrary("platform-extensions", PathUtil.getJarPathForClass(ComponentManager::class.java))
    moduleBuilder.addLibrary("platform-util-base", PathUtil.getJarPathForClass(ProcessCanceledException::class.java))
    moduleBuilder.addLibrary("util-rt", PathUtil.getJarPathForClass(ThrowableComputable::class.java))
    // mock JDK is required for getting `CancellationException`, otherwise `ProcessCancelledException` does not inherit from `RuntimeException`
    moduleBuilder.addJdk(IdeaTestUtil.getMockJdk18Path().path)
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_17)
  }

  override fun setUp() {
    super.setUp()
    ModuleRootModificationUtil.updateModel(module) {
      KotlinTester.configureKotlinStdLib(it)
    }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    myFixture.enableInspections(PotentialDeadlockInServiceInitializationInspection())
  }

}
