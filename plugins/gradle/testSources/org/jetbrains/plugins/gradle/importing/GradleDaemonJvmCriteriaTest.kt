// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import org.gradle.internal.jvm.inspection.JvmVendor.KnownJvmVendor.JETBRAINS
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder
import org.jetbrains.plugins.gradle.properties.GradleDaemonJvmPropertiesFile
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmCriteria
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import java.util.concurrent.TimeUnit

class GradleDaemonJvmCriteriaTest : GradleImportingTestCase() {

  @Test
  @TargetVersions("8.8+")
  fun testUpdatingDaemonJvmCriteria() {
    createSettingsFile {
      withJavaToolchainPlugin()
    }
    importProject()

    val daemonJvmCriteria = GradleDaemonJvmCriteria("17", JETBRAINS.asJvmVendor())
    GradleDaemonJvmHelper.updateProjectDaemonJvmCriteria(myProject, projectRoot.path, daemonJvmCriteria)
      .get(1, TimeUnit.MINUTES)

    assertEquals(daemonJvmCriteria, GradleDaemonJvmPropertiesFile.getProperties(projectRoot.toNioPath()).criteria)
  }

  companion object {

    private const val JAVA_TOOLCHAIN_REPOSITORY = "custom"
    private const val JAVA_TOOLCHAIN_SERVER = "https://server.com"

    private fun GradleSettingScriptBuilder<*>.withJavaToolchainPlugin() {
      assert(GradleDaemonJvmHelper.isDaemonJvmCriteriaSupported(gradleVersion))
      addCode("""
        |import java.util.Optional
        |
        |abstract class JavaToolchainPlugin implements Plugin<Settings> {
        |
        |    @Inject
        |    protected abstract JavaToolchainResolverRegistry getJavaToolchainResolverRegistry()
        |
        |    void apply(Settings settings) {
        |        javaToolchainResolverRegistry.register(Resolver)
        |        settings.plugins.apply('jvm-toolchain-management')
        |        settings.toolchainManagement.jvm.javaRepositories.repository('$JAVA_TOOLCHAIN_REPOSITORY') {
        |            resolverClass = Resolver
        |        }
        |    }
        |
        |    abstract static class Resolver implements JavaToolchainResolver {
        |        @Override
        |        Optional<JavaToolchainDownload> resolve(JavaToolchainRequest request) {
        |            return Optional.of(JavaToolchainDownload.fromUri(URI.create('$JAVA_TOOLCHAIN_SERVER')))
        |        }
        |    }
        |}
        |
        |apply plugin: JavaToolchainPlugin
      """.trimMargin())
    }
  }
}