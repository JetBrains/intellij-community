// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.IntentionActionBean
import com.intellij.codeInsight.intention.impl.config.IntentionManagerImpl
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class IntentionDescriptionTest : LightPlatformTestCase() {

    private val necessaryNormalNames = listOf("description.html", "before.kt.template", "after.kt.template")
    private val necessaryXmlNames = listOf("description.html", "before.xml.template", "after.xml.template")
    private val necessaryMavenNames = listOf("description.html")
    private val directories = listOf(
        KotlinRoot.DIR.resolve("idea/resources-en/intentionDescriptions"),
        KotlinRoot.DIR.resolve("code-insight/intentions-shared/resources-en/intentionDescriptions"),
        KotlinRoot.DIR.resolve("code-insight/descriptions/resources-en/intentionDescriptions"),
    )

    fun testDescriptionsAndShortNames() {
        val intentionTools = loadKotlinIntentions()
        val errors = StringBuilder()
        for (tool in intentionTools) {
            val className = tool.className
            val shortName = className.substringAfterLast(".").replace("$", "")
            val directory = directories.map { it.resolve(shortName)}.filter { it.exists() }.firstOrNull()
            if (directory == null || !directory.exists() || !directory.isDirectory) {
                if (tool.categories != null) {
                    errors.append("No description directory for intention '").append(className).append("'\n")
                }
            } else {
                val necessaryNames = when {
                    shortName.isMavenIntentionName() -> necessaryMavenNames
                    shortName.isXmlIntentionName() -> necessaryXmlNames
                    else -> necessaryNormalNames
                }
                for (fileName in necessaryNames) {
                    val file = directory.resolve(fileName)
                    if (!file.exists() || !file.isFile) {
                        errors.append("No description file $fileName for intention '").append(className).append("'\n")
                    }
                }
            }
        }

        UsefulTestCase.assertEmpty(errors.toString())
    }

    private fun String.isMavenIntentionName() = startsWith("MavenPlugin")

    private fun String.isXmlIntentionName() = startsWith("Add") && endsWith("ToManifest")

    private fun loadKotlinIntentions(): List<IntentionActionBean> {
        return IntentionManagerImpl.EP_INTENTION_ACTIONS.extensionList.filter {
            it.pluginDescriptor.pluginId == KotlinIdePlugin.id
        }
    }
}