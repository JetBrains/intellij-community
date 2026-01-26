// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.completion

import com.intellij.gradle.completion.indexer.GradleLocalRepositoryIndexer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.scripting.k2.K2GradleCodeInsightTestCase
import com.intellij.gradle.completion.indexer.GradleLocalRepositoryIndexerTestImpl
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.junit.jupiter.params.ParameterizedTest
import kotlin.jvm.java

class KotlinGradleLocalDependencyCompletionTest : K2GradleCodeInsightTestCase() {

    @TestDisposable private lateinit var disposable: Disposable

    private fun configureLocalIndex(vararg gavArgs: String) {
        ApplicationManager.getApplication().replaceService(
            GradleLocalRepositoryIndexer::class.java,
            GradleLocalRepositoryIndexerTestImpl(LocalEelDescriptor, gavArgs.toList()),
            disposable
        )
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test no suggestions`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains:annotations:13.0",
                "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation("org.jetbrains.kotlin:kotlin-something-weir<caret>")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test version suggestions after artifact`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation("org.jetbrains.kotlin:kotlin-reflect<caret>")
                }
                """.trimIndent(),
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test version suggestions after a colon`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation("org.jetbrains.kotlin:kotlin-reflect:<caret>")
                }
                """.trimIndent(),
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test version suggestions`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation("org.jetbrains.kotlin:kotlin-reflect:1<caret>")
                }
                """.trimIndent(),
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test version suggestions after a dot`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation("org.jetbrains.kotlin:kotlin-reflect:1.<caret>")
                }
                """.trimIndent(),
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test version auto completion`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
                "com.google.guava:guava:33.5.0-jre",
            )
            testAutoCompletion(
                """
                dependencies {
                    implementation("org.jetbrains.kotlin:kotlin-reflect:1.1<caret>")
                }
                """.trimIndent(),
                """
                dependencies {
                    implementation("org.jetbrains.kotlin:kotlin-reflect:1.1.0")
                }
                """.trimIndent(),
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test custom configuration`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    customConf("org.jetbrains.kotlin:kotlin-reflect:<caret>")
                }
                """.trimIndent(),
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test custom string configuration`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    "customConf"("org.jetbrains.kotlin:kotlin-reflect:<caret>")
                }
                """.trimIndent(),
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test suggestions after org`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
                "org.gradle:gradle-tooling-api:9.2.1",
                "org.gradle:gradle-tooling-api:9.2.0",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation("org.<caret>")
                }
                """.trimIndent(),
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
                "org.gradle:gradle-tooling-api:9.2.1",
                "org.gradle:gradle-tooling-api:9.2.0",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test extra artifact suggestions`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
                "org.jetbrains.kotlin:kotlin-stdlib:2.0.0",
                "org.jetbrains.kotlin:kotlin-stdlib:1.1.0",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.0",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation("org.jetbrains.kotlin:kotlin-stdlib:2<caret>")
                }
                """.trimIndent(),
                "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
                "org.jetbrains.kotlin:kotlin-stdlib:2.0.0",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.0",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test artifact suggestions caret after group`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains:annotations:13.0",
                "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
                "org.jetbrains.kotlin:kotlin-stdlib:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "org.gradle:gradle-tooling-api:9.2.1",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation("org.jetbrains.kotlin<caret>")
                }
                """.trimIndent(),
                "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
                "org.jetbrains.kotlin:kotlin-stdlib:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test artifact suggestions caret after group colon`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains:annotations:13.0",
                "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
                "org.jetbrains.kotlin:kotlin-stdlib:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "org.gradle:gradle-tooling-api:9.2.1",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation("org.jetbrains.kotlin:<caret>")
                }
                """.trimIndent(),
                "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
                "org.jetbrains.kotlin:kotlin-stdlib:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test artifact suggestions caret in artifact`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains:annotations:13.0",
                "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
                "org.jetbrains.kotlin:kotlin-stdlib:2.0.0",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.0",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.gradle:gradle-tooling-api:9.2.1",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation("org.jetbrains.kotlin:kotlin-s<caret>")
                }
                """.trimIndent(),
                "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
                "org.jetbrains.kotlin:kotlin-stdlib:2.0.0",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.0",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.1.0",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test caret in empty string`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains:annotations:13.0",
                "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
                "org.jetbrains.kotlin:kotlin-stdlib:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "org.gradle:gradle-tooling-api:9.2.1",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation("<caret>")
                }
                """.trimIndent(),
                "org.jetbrains:annotations:13.0",
                "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
                "org.jetbrains.kotlin:kotlin-stdlib:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "org.gradle:gradle-tooling-api:9.2.1",
                "com.google.guava:guava:33.5.0-jre",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test single part being artifact`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0",
                "org.jetbrains:gradle-plugin:2.3.0",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation("gradle<caret>")
                }
                """.trimIndent(),
                "org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0",
                "org.jetbrains:gradle-plugin:2.3.0",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test single part being group`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.gradle:api:9.0.0",
                "gradle:artifact:version",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation("gradle<caret>")
                }
                """.trimIndent(),
                "org.gradle:api:9.0.0",
                "gradle:artifact:version",
                "org.gradle:api:9.0.0",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test single part being artifact or group`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.gradle:gradle-tooling-api:9.2.1",
                "org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0",
                "org.gradle:api:9.0.0",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation("gradle<caret>")
                }
                """.trimIndent(),
                "org.gradle:gradle-tooling-api:9.2.1",
                "org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0",
                "org.gradle:api:9.0.0",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test each part uses contains`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "group:artifact:version",
                "prefix-groupsuffix:prefixartifact-suffix:prefix.version-suffix",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation("group:artifact:version<caret>")
                }
                """.trimIndent(),
                "group:artifact:version",
                "prefix-groupsuffix:prefixartifact-suffix:prefix.version-suffix",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test no suggestions outside dependencies block`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
            )
            testCompletionStrict(
                """
                // no dependencies block here
                val x = "org.jetbrains.kotlin:kotlin-reflect:<caret>"
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test no suggestions in comments`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
            )
            testCompletionStrict(
                """
                dependencies {
                    // implementation("org.jetbrains.kotlin:kotlin-reflect:<caret>")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test whitespace inside string is tolerated`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation( "org.jetbrains.kotlin:kotlin-reflect:<caret>" )
                }
                """.trimIndent(),
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test caret after a dash`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation("org.jetbrains.kotlin:kotlin-<caret>")
                }
                """.trimIndent(),
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test named dependencyNotation argument`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation(dependencyNotation = "org.jetbrains.kotlin:kotlin-r<caret>")
                }
                """.trimIndent(),
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
            )
        }
    }

    // NAMED ARGUMENTS NOTATION

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test named arguments empty group with no name and no version`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains:annotations:13.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "org.gradle:gradle-tooling-api:9.2.1",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation(group = "<caret>")
                }
                """.trimIndent(),
                "org.jetbrains",
                "org.jetbrains.kotlin",
                "org.gradle",
                "com.google.guava",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test named arguments partial group with no name and no version`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains:annotations:13.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "org.gradle:gradle-tooling-api:9.2.1",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation(group = "org.jetbrains<caret>")
                }
                """.trimIndent(),
                "org.jetbrains",
                "org.jetbrains.kotlin",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test named arguments empty group with name and empty version`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains:annotations:13.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "org.gradle:gradle-tooling-api:9.2.1",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation(group = "<caret>", name = "kotlin-reflect", version = "")
                }
                """.trimIndent(),
                "org.jetbrains.kotlin",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test named arguments partial group with name and empty version`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains:annotations:13.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "org.gradle:gradle-tooling-api:9.2.1",
                "com.google.guava:guava:33.5.0-jre",
            )
            testAutoCompletion(
                """
                dependencies {
                    implementation(group = "org.jetbrains<caret>", name = "kotlin-reflect", version = "")
                }
                """.trimIndent(),
                """
                dependencies {
                    implementation(group = "org.jetbrains.kotlin", name = "kotlin-reflect", version = "")
                }
                """.trimIndent(),
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test named arguments empty group with empty name and empty version`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains:annotations:13.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "org.gradle:gradle-tooling-api:9.2.1",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation(group = "<caret>", name = "", version = "")
                }
                """.trimIndent(),
                "org.jetbrains",
                "org.jetbrains.kotlin",
                "org.gradle",
                "com.google.guava",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test named arguments empty group with name and no version`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains:annotations:13.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation(group = "<caret>", name = "kotlin-reflect")
                }
                """.trimIndent(),
                "org.jetbrains.kotlin",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test named arguments empty version with group and name`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains:annotations:13.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation(group = "org.jetbrains.kotlin", name = "kotlin-reflect", version = "<caret>")
                }
                """.trimIndent(),
                "2.0.0",
                "1.1.0",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test named arguments empty name with group and empty version`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains:annotations:13.0",
                "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation(group = "org.jetbrains.kotlin", name = "<caret>", version = "")
                }
                """.trimIndent(),
                "kotlin-stdlib",
                "kotlin-reflect",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test named arguments partial name with group and empty version`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains:annotations:13.0",
                "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation(group = "org.jetbrains.kotlin", name = "kotlin-s<caret>", version = "")
                }
                """.trimIndent(),
                "kotlin-stdlib",
                "kotlin-stdlib-jdk8",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test out-of-order named arguments empty group with name and no version`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains:annotations:13.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation(name = "kotlin-reflect", group = "<caret>")
                }
                """.trimIndent(),
                "org.jetbrains.kotlin",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test out-of-order named arguments empty group with name and empty version`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains:annotations:13.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation(name = "kotlin-reflect", group = "<caret>", version = "")
                }
                """.trimIndent(),
                "org.jetbrains.kotlin",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test positional arguments empty group with name and empty version`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains:annotations:13.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation("<caret>", "kotlin-reflect", "")
                }
                """.trimIndent(),
                "org.jetbrains.kotlin",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test positional arguments empty name with group and no version`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains:annotations:13.0",
                "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation("org.jetbrains.kotlin", "<caret>")
                }
                """.trimIndent(),
                "kotlin-stdlib",
                "kotlin-reflect",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test positional arguments empty name with group and empty version`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains:annotations:13.0",
                "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation("org.jetbrains.kotlin", "<caret>", "")
                }
                """.trimIndent(),
                "kotlin-stdlib",
                "kotlin-reflect",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test positional arguments partial name with group and no version`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains:annotations:13.0",
                "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation("org.jetbrains.kotlin", "kotlin-s<caret>")
                }
                """.trimIndent(),
                "kotlin-stdlib",
                "kotlin-stdlib-jdk8",
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test positional arguments empty version with group and name`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            configureLocalIndex(
                "org.jetbrains:annotations:13.0",
                "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
                "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
                "com.google.guava:guava:33.5.0-jre",
            )
            testCompletionStrict(
                """
                dependencies {
                    implementation("org.jetbrains.kotlin", "kotlin-reflect", "<caret>")
                }
                """.trimIndent(),
                "2.0.0",
                "1.1.0",
            )
        }
    }
}