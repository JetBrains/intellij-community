// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.compilerPreferences.facet

import com.intellij.facet.ui.FacetEditor
import com.intellij.facet.ui.FacetEditorsFactory
import com.intellij.facet.ui.MultipleFacetSettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.idea.base.compilerPreferences.configuration.KotlinCompilerConfigurableTab
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import javax.swing.JComponent

class MultipleKotlinFacetEditor(
    private val project: Project,
    private val editors: Array<out FacetEditor>
) : MultipleFacetSettingsEditor() {
    private val helper = FacetEditorsFactory.getInstance().createMultipleFacetEditorHelper()

    private val FacetEditor.tabEditor: KotlinFacetEditorGeneralTab.EditorComponent
        get() = editorTabs.firstIsInstance<KotlinFacetEditorGeneralTab>().editor

    private val FacetEditor.compilerConfigurable: KotlinCompilerConfigurableTab
        get() = tabEditor.compilerConfigurable

    private val multiEditorComponent: Lazy<KotlinFacetEditorGeneralTab.EditorComponent> = lazy(LazyThreadSafetyMode.NONE) {
        KotlinFacetEditorGeneralTab.EditorComponent(project, configuration = null).apply {
            initialize()

            helper.bind(useProjectSettingsCheckBox, editors) { it.tabEditor.useProjectSettingsCheckBox }
            //TODO(auskov): Support bulk editing target platforms?
            with(compilerConfigurable) {
                helper.bind(reportWarningsCheckBox, editors) { it.compilerConfigurable.reportWarningsCheckBox }
                helper.bind(additionalArgsOptionsField.textField, editors) { it.compilerConfigurable.additionalArgsOptionsField.textField }
                helper.bind(generateSourceMapsCheckBox, editors) { it.compilerConfigurable.generateSourceMapsCheckBox }
                helper.bind(outputDirectory.textField, editors) { it.compilerConfigurable.outputDirectory.textField }
                helper.bind(copyRuntimeFilesCheckBox, editors) { it.compilerConfigurable.copyRuntimeFilesCheckBox }
                helper.bind(keepAliveCheckBox, editors) { it.compilerConfigurable.keepAliveCheckBox }
                helper.bind(moduleKindComboBox, editors) { it.compilerConfigurable.moduleKindComboBox }
                helper.bind(languageVersionComboBox, editors) { it.compilerConfigurable.languageVersionComboBox }
                helper.bind(apiVersionComboBox, editors) { it.compilerConfigurable.apiVersionComboBox }
            }
        }
    }

    override fun createComponent(): JComponent = multiEditorComponent.value

    override fun disposeUIResources() {
        helper.unbind()
        // Reset tabs with selected "Use project settings" after switching off the multi-editor mode.
        // Their settings might have changed to non-project one due to UI control binding
        editors.map { it.tabEditor }.filter { it.useProjectSettingsCheckBox.isSelected }.forEach { it.updateCompilerConfigurable() }

        // `editors` were created on the outside and will be disposed there;
        // we only have to worry about the `multiEditorComponent` that we created
        if (multiEditorComponent.isInitialized()) {
            Disposer.dispose(multiEditorComponent.value)
        }
    }
}
