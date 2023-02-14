// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradle.configuration.utils

import com.intellij.openapi.util.IntellijInternalApi

// TODO: Replace heuristic with proper import!
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
annotation class UnsafeTestSourceSetHeuristicApi

@UnsafeTestSourceSetHeuristicApi
@IntellijInternalApi
fun predictedProductionSourceSetName(testSourceSetName: String): String {
    /*
    This implementation uses a lot of implicit knowledge and should be replaced.
    This implicit knowledge is/was used at other places as well that should also
    use an explicit API once available.

    See:
    https://github.com/JetBrains/intellij-community/blob/c05e8d6de8d1c690eb99c2fff8629c0de373c48f/plugins/gradle/src/org/jetbrains/plugins/gradle/service/project/CommonGradleProjectResolverExtension.java#L153
    https://github.com/JetBrains/intellij-kotlin/blob/fe6ee9dfb42ad1cdd9e55449e2481125f1631133/gradle/gradle-idea/src/org/jetbrains/kotlin/idea/configuration/KotlinMPPGradleProjectResolver.kt#L424
    https://github.com/JetBrains/intellij-kotlin/blob/fe6ee9dfb42ad1cdd9e55449e2481125f1631133/gradle/gradle-idea/src/org/jetbrains/kotlin/idea/configuration/KotlinMPPGradleProjectResolver.kt#L627
     */
    if (testSourceSetName.endsWith("Test")) {
        return testSourceSetName.removeSuffix("Test") + "Main"
    }
    if (testSourceSetName == "test") {
        return "main"
    }
    return testSourceSetName + "Main"
}