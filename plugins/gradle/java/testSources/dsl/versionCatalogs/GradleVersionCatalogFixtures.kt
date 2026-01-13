// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl.versionCatalogs

import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile

internal object GradleVersionCatalogFixtures {

  internal val BASE_VERSION_CATALOG_FIXTURE = GradleTestFixtureBuilder.create("GradleVersionCatalogs-base") { gradleVersion ->
    withSettingsFile(gradleVersion) {
      include("subproject1")
    }
    withBuildFile(gradleVersion, "subproject1")
    withFile("gradle/libs.versions.toml", "")
  }

  /**
   * In this case, subproject path in `include(...)` is not a literal argument, but a calculated value.
   * For the logic, that relies on `settings.gradle` file parsing to determine version catalogs available to a subproject,
   * this case is not supported. It should be supported by the logic relying on the Gradle sync data.
   */
  internal val DYNAMICALLY_INCLUDED_SUBPROJECTS_FIXTURE =
    GradleTestFixtureBuilder.create("GradleVersionCatalogs-dynamically-included-subprojects") { gradleVersion ->
      withSettingsFile(gradleVersion) {
        addCode("""
          void includeSubprojectsDynamically(String path) {
            for (File subDir : file(path).listFiles()) {
              def buildScript = new File(subDir, "build.gradle")
              if (!buildScript.exists()) continue
              include(":" + path.replace("/", ":") + ":" + subDir.name)
            }
          }
          includeSubprojectsDynamically("subprojectsDir")
          
          dependencyResolutionManagement {
            versionCatalogs {
              "customLibs" {
                  from(files("customPath/custom.toml"))
              }
            }
          }  
          """.trimIndent()
        )
      }
      withFile("customPath/custom.toml", /* language=TOML */ """
        [libraries]
        apache-groovy = { module = "org.apache.groovy:groovy", version = "4.0.0" }
        """.trimIndent()
      )
      withBuildFile(gradleVersion, "subprojectsDir/subproject1")
    }

  internal val VERSION_CATALOG_COMPOSITE_BUILD_FIXTURE =
    GradleTestFixtureBuilder.create("GradleVersionCatalogs-composite-build") { gradleVersion ->
      withSettingsFile(gradleVersion) {
        includeBuild("includedBuild1")
      }
      withBuildFile(gradleVersion, "includedBuild1")
      withFile("includedBuild1/gradle/libs.versions.toml", "")
    }
}
