// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.toolchain.gradle

import com.intellij.idea.IJIgnore
import com.intellij.openapi.application.readAction
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.JAVA_HOME
import com.intellij.openapi.roots.ui.configuration.SdkTestCase.Companion.withRegisteredSdks
import kotlinx.coroutines.runBlocking
import org.jetbrains.jps.model.java.JdkVersionDetector
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.getTopLevelBuildScriptSettingsPsiFile
import org.jetbrains.kotlin.idea.gradleJava.toolchain.GradleDaemonJvmCriteriaMigrationHelper
import org.jetbrains.plugins.gradle.importing.GradleProjectSdkResolverTestCase
import org.jetbrains.plugins.gradle.properties.GradleDaemonJvmPropertiesFile
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmCriteria
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.jetbrains.plugins.gradle.util.toJvmCriteria
import org.junit.Test
import org.junit.jupiter.api.Assertions
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path

class GradleDaemonToolchainMigrationHelperTest: GradleProjectSdkResolverTestCase() {

    @Test
    @TargetVersions("8.8+")
    @IJIgnore(issue = "IDEA-369675")
    fun `test Given project When migrate to Daemon toolchain Then criteria is maintained`() = runBlocking {
        val jdk = resolveRealTestSdk()
        val jdkInfo = JdkVersionDetector.getInstance().detectJdkVersionInfo(jdk.homePath!!)
        check(jdkInfo != null)
        createGradleSubProject()

        environment.withVariables(JAVA_HOME to jdk.homePath) {
            withRegisteredSdks(jdk) {
                loadProject()
                check(GradleDaemonJvmPropertiesFile.getProperties(Path(projectPath)) == null)

                GradleDaemonJvmCriteriaMigrationHelper.migrateToDaemonJvmCriteria(myProject, projectPath)
                    .get(1, TimeUnit.MINUTES)

                assertFoojayPluginIsApplied()
                assertDaemonJvmProperties(jdkInfo.toJvmCriteria())
            }
        }
    }

    private suspend fun assertFoojayPluginIsApplied() {
        readAction {
            val settingsFile = getTopLevelBuildScriptSettingsPsiFile(myProject, projectPath)!!
            settingsFile.text.contains("org.gradle.toolchains.foojay-resolver-convention")
        }
    }

    private fun assertDaemonJvmProperties(expectedCriteria: GradleDaemonJvmCriteria) {
        val properties = GradleDaemonJvmPropertiesFile.getProperties(Path(projectPath))
        Assertions.assertNotNull(properties)
        Assertions.assertEquals(expectedCriteria, properties!!.criteria)
    }
}