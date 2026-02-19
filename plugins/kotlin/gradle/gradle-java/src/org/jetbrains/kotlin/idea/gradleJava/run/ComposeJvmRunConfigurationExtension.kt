// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.run

import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.service.execution.configuration.ExternalSystemReifiedRunConfigurationExtension
import com.intellij.openapi.externalSystem.service.execution.configuration.fragments.SettingsEditorFragmentContainer
import com.intellij.openapi.externalSystem.service.execution.configuration.fragments.addRemovableLabeledTextSettingsEditorFragment
import com.intellij.openapi.externalSystem.service.ui.util.LabeledSettingsFragmentInfo
import com.intellij.openapi.util.Key
import com.intellij.ui.components.JBTextField
import org.jdom.Element
import org.jetbrains.kotlin.idea.gradleJava.KotlinJavaGradleBundle
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration

private val isComposeJvmKey = Key.create<Boolean>("isComposeJvm")
private val mainFunctionClassFqnKey = Key.create<String>("mainClassFqn")

var ExternalSystemRunConfiguration.isComposeJvm: Boolean
    get() = getUserData<Boolean>(isComposeJvmKey) == true
    set(value) = putUserData<Boolean>(isComposeJvmKey, value)

var ExternalSystemRunConfiguration.mainFunctionClassFqn: String?
    get() = getUserData<String>(mainFunctionClassFqnKey) as? String
    set(value) = putUserData<String>(mainFunctionClassFqnKey, value)


internal class ComposeJvmRunConfigurationExtension :
    ExternalSystemReifiedRunConfigurationExtension<GradleRunConfiguration>(GradleRunConfiguration::class.java) {

    private companion object {
        const val ROOT_KEY = "ComposeJvm"
        const val MAIN_FUNCTION_FILE_KEY = "mainFunctionFile"
    }

    override fun SettingsEditorFragmentContainer<GradleRunConfiguration>.configureFragments(
        configuration: GradleRunConfiguration
    ) {
        // fragment will only be visible + accessible if set programmatically for this run configuration
        if (!configuration.isComposeJvm) return

        addRemovableLabeledTextSettingsEditorFragment(
            settingsFragmentInfo = object : LabeledSettingsFragmentInfo {
            override val editorLabel: String = KotlinJavaGradleBundle.message("settings.run.configuration.compose.jvm.main.class.name")
            override val settingsId: String = "compose.jvm.main.class.fragment"
            override val settingsName: String = KotlinJavaGradleBundle.message("settings.run.configuration.compose.jvm.main.class.name")
            override val settingsGroup: String = KotlinJavaGradleBundle.message("settings.run.configuration.compose.jvm.main.class.group")
            override val settingsHint: String = KotlinJavaGradleBundle.message("settings.run.configuration.compose.jvm.main.class.hint")
            override val settingsActionHint: String? = null
        },
            createComponent = { JBTextField() },
            getter = { configuration.mainFunctionClassFqn },
            setter = { configuration.mainFunctionClassFqn = it ?: "" },
            default = { null })
    }

    override fun writeExternal(runConfiguration: ExternalSystemRunConfiguration, element: Element) {
        super.writeExternal(runConfiguration, element)
        if (!runConfiguration.isComposeJvm) return

        element.addContent(Element(ROOT_KEY).apply {
            addContent(Element(MAIN_FUNCTION_FILE_KEY).apply { text = runConfiguration.mainFunctionClassFqn })
        })
    }

    override fun readExternal(runConfiguration: ExternalSystemRunConfiguration, element: Element) {
        super.readExternal(runConfiguration, element)
        val myElement = element.getChild(ROOT_KEY) ?: return

        runConfiguration.isComposeJvm = true
        runConfiguration.mainFunctionClassFqn = myElement.getChild(MAIN_FUNCTION_FILE_KEY)?.text ?: ""
    }
}
