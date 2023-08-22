// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.kotlin.idea.gradle.KotlinIdeaGradleBundle
import org.jetbrains.plugins.gradle.codeInsight.GradlePluginDescriptionsExtension

class KotlinGradlePluginDescription : GradlePluginDescriptionsExtension {
    override fun getPluginDescriptions():  Map<@NlsSafe String, @NlsContexts.DetailedDescription String> =
        mapOf("kotlin" to KotlinIdeaGradleBundle.message("description.text.adds.support.for.building.kotlin.projects"))
}
