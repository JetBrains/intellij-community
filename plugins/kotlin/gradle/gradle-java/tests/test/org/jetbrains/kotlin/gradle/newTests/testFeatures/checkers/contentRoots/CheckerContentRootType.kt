// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.contentRoots

internal sealed class CheckerContentRootType(val description: String) {

    sealed class RegularRoot(
        description: String,
        val owner: Owner,
        val isTest: Boolean,
        val isResources: Boolean /* false means it's sources */
    ) : CheckerContentRootType(description) {
        object Kotlin {
            object MainSources : RegularRoot("", Owner.Kotlin, isTest = false, isResources = false)
            object TestSources : RegularRoot("Test", Owner.Kotlin, isTest = true, isResources = false)

            object MainResources : RegularRoot("Resources", Owner.Kotlin, isTest = false, isResources = true)
            object TestResources : RegularRoot("Test, Resources", Owner.Kotlin, isTest = true, isResources = true)
        }

        object Java {
            object MainSources : RegularRoot("Java", Owner.Java, isTest = false, isResources = false)
            object TestSources : RegularRoot("Java, Test", Owner.Java, isTest = true, isResources = false)

            object MainResources : RegularRoot("Java, Resources", Owner.Java, isTest = false, isResources = true)
            object TestResources : RegularRoot("Java, Test, Resources", Owner.Java, isTest = true, isResources = true)
        }

        enum class Owner {
            Kotlin,
            Java,
        }
    }

    // Android-specific folders
    class Android(folderName: String) : CheckerContentRootType("Android, $folderName") {
        companion object {
            val ANDROID_SPECIFIC_FOLDER_NAMES = setOf(
                "shaders", "rs", "aidl"
            )
        }
    }

    object Generated : CheckerContentRootType("Generated")

    class Other(description: String) : CheckerContentRootType(description)
}
