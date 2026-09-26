// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.compilerPreferences.configuration

import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.selected
import com.intellij.util.ui.ThreeStateCheckBox
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.cli.common.arguments.K2JsArgumentConstants
import org.jetbrains.kotlin.config.VersionView
import org.jetbrains.kotlin.idea.base.compilerPreferences.KotlinBaseCompilerConfigurationUiBundle.message
import javax.swing.JCheckBox
import javax.swing.JEditorPane
import javax.swing.JTextField

private const val MAX_WARNING_SIZE = 75

internal class KotlinCompilerConfigurableUi(private val onCopyRuntimeFilesCheckBoxChange: Runnable) {

    @JvmField
    val jpsPluginComboBoxModel = ComboBoxModelWithPossiblyDisabledItems()
    private val sourceMapSourceEmbedding = mapOf(
        K2JsArgumentConstants.SOURCE_MAP_SOURCE_CONTENT_NEVER to message("configuration.description.never"),
        K2JsArgumentConstants.SOURCE_MAP_SOURCE_CONTENT_ALWAYS to message("configuration.description.always"),
        K2JsArgumentConstants.SOURCE_MAP_SOURCE_CONTENT_INLINING to message("configuration.description.when.inlining.a.function.from.other.module.with.embedded.sources")
    )
    private val moduleKind = mapOf(
        K2JsArgumentConstants.MODULE_PLAIN to message("configuration.description.plain.put.to.global.scope"),
        K2JsArgumentConstants.MODULE_AMD to message("configuration.description.amd"),
        K2JsArgumentConstants.MODULE_COMMONJS to message("configuration.description.commonjs"),
        K2JsArgumentConstants.MODULE_UMD to message("configuration.description.umd.detect.amd.or.commonjs.if.available.fallback.to.plain")
    )

    private lateinit var warningLabel: JEditorPane
    lateinit var reportWarningsCheckBox: ThreeStateCheckBox
    lateinit var kotlinJpsPluginVersionComboBox: ComboBox<JpsVersionItem>
    lateinit var languageVersionComboBox: ComboBox<VersionView>
    lateinit var apiVersionComboBox: ComboBox<VersionView>
    lateinit var additionalArgsOptionsField: RawCommandLineEditor
    lateinit var generateSourceMapsCheckBox: ThreeStateCheckBox
    lateinit var outputDirectory: TextFieldWithBrowseButton

    // Workaround: ThreeStateCheckBox doesn't send suitable notification on state change
    // TODO: replace with PropertyChangerListener after fix is available in IDEA
    @JvmField
    val copyRuntimeFilesCheckBox =
        object : ThreeStateCheckBox(message("kotlin.compiler.js.option.output.copy.files")) {
            override fun setState(state: State?) {
                super.setState(state)
                onCopyRuntimeFilesCheckBoxChange.run()
            }
        }
    lateinit var keepAliveCheckBox: ThreeStateCheckBox
    lateinit var enableIncrementalCompilationForJvmCheckBox: JCheckBox
    lateinit var enableIncrementalCompilationForJsCheckBox: JCheckBox
    lateinit var moduleKindComboBox: ComboBox<@NonNls String>
    lateinit var jvmVersionComboBox: ComboBox<String>
    lateinit var sourceMapPrefix: JTextField
    lateinit var sourceMapEmbedSources: ComboBox<@NonNls String>

    lateinit var k2jvmGroup: Row
    lateinit var k2jsGroup: Row
    lateinit var kotlinJpsPluginVersionRow: Row
    lateinit var outputDirectoryRow: Row
    private lateinit var warningRow: Row

    @JvmField
    val panel = panel {
        warningRow = row {
            icon(AllIcons.General.WarningDialog)

            warningLabel = text("").component
        }.visible(false)

        row {
            reportWarningsCheckBox =
                threeStateCheckBox(message("kotlin.compiler.option.generate.no.warnings"))
                    .component
        }

        kotlinJpsPluginVersionRow = row(message("kotlin.compiler.version")) {
            kotlinJpsPluginVersionComboBox = comboBox(jpsPluginComboBoxModel)
                .align(AlignX.FILL)
                .component
        }

        row(message("language.version")) {
            // Explicit use of MutableCollectionComboBoxModel guarantees that setSelectedItem() can make safe cast.
            languageVersionComboBox = comboBox(MutableCollectionComboBoxModel<VersionView>())
                .align(AlignX.FILL)
                .component
        }

        row(message("api.version")) {
            // Explicit use of MutableCollectionComboBoxModel guarantees that setSelectedItem() can make safe cast.
            apiVersionComboBox = comboBox(MutableCollectionComboBoxModel<VersionView>())
                .align(AlignX.FILL)
                .component
        }

        row(message("kotlin.compiler.option.additional.command.line.parameters")) {
            additionalArgsOptionsField = cell(RawCommandLineEditor())
                .align(AlignX.FILL)
                .component
        }

        row {
            keepAliveCheckBox =
                threeStateCheckBox(message("keep.compiler.process.alive.between.invocations"))
                    .component
        }

        k2jvmGroup = group(message("kotlin.compiler.jvm.option.panel.title")) {
            row {
                enableIncrementalCompilationForJvmCheckBox = checkBox(
                    message("enable.incremental.compilation")
                ).component
            }
            row(message("target.jvm.version")) {
                jvmVersionComboBox = comboBox(emptyList<String>())
                    .align(AlignX.FILL)
                    .component
            }
        }

        k2jsGroup = group(message("kotlin.compiler.js.option.panel.title")) {
            row {
                enableIncrementalCompilationForJsCheckBox =
                    checkBox(message("enable.incremental.compilation"))
                        .component
            }
            row {
                generateSourceMapsCheckBox =
                    threeStateCheckBox(message("kotlin.compiler.js.option.generate.sourcemaps"))
                        .component
            }
            row("") {
                sourceMapPrefix = textField()
                    .align(AlignX.FILL)
                    .enabledIf(generateSourceMapsCheckBox.selected)
                    .component
            }
            row(message("embed.source.code.into.source.map")) {
                sourceMapEmbedSources = comboBox(sourceMapSourceEmbedding.keys, textListCellRenderer("") {
                    sourceMapSourceEmbedding.getOrElse(it) {
                        thisLogger().warn("Unknown source map source content option: $it")
                        ""
                    }
                }).align(AlignX.FILL)
                    .component
            }
            row {
                cell(copyRuntimeFilesCheckBox)
            }
            indent {
                outputDirectoryRow = row(message("destination.directory")) {
                    outputDirectory = cell(TextFieldWithBrowseButton())
                        .align(AlignX.FILL)
                        .applyToComponent {
                            text = message("kotlin.compiler.lib")
                        }
                        .component
                }
            }
            row(message("module.kind")) {
                moduleKindComboBox = comboBox(moduleKind.keys, textListCellRenderer("") {
                    moduleKind.getOrElse(it) {
                        thisLogger().warn("Unexpected module kind: $it")
                        ""
                    }
                }).align(AlignX.FILL)
                    .component
            }
        }
    }

    fun updateWarning(modulesOverridingProjectSettings: List<@NlsSafe String>) {
        if (modulesOverridingProjectSettings.isEmpty()) {
            warningRow.visible(false)
            return
        }

        warningLabel.text = buildOverridingModulesWarning(modulesOverridingProjectSettings)
        warningRow.visible(true)
    }

    @Nls
    private fun buildOverridingModulesWarning(modulesOverridingProjectSettings: List<@NlsSafe String>): String {
        val nameCountToShow = calculateNameCountToShowInWarning(modulesOverridingProjectSettings)
        val allNamesCount = modulesOverridingProjectSettings.size
        if (nameCountToShow == 0) {
            return message("configuration.warning.text.modules.override.project.settings", allNamesCount.toString())
        }

        var modulesAsString = modulesOverridingProjectSettings
            .subList(0, nameCountToShow)
            .joinToString(", ") { "<strong>$it</strong>" }
        if (nameCountToShow < allNamesCount) {
            modulesAsString += message("configuration.text.and.other", (allNamesCount - nameCountToShow).toString())
        }

        return message("configuration.warning.text.following.modules.override.project.settings", modulesAsString)
    }

    private fun calculateNameCountToShowInWarning(allNames: List<String>): Int {
        var lengthSoFar = 0
        val size = allNames.size
        for (i in 0..<size) {
            if (i > 0) {
                lengthSoFar += 2
            }
            lengthSoFar += allNames[i].length
            if (lengthSoFar > MAX_WARNING_SIZE) {
                return i
            }
        }
        return size
    }
}

// Explicit use of MutableCollectionComboBoxModel guarantees that setSelectedItem() can make safe cast.
internal class ComboBoxModelWithPossiblyDisabledItems : MutableCollectionComboBoxModel<JpsVersionItem>() {
    override fun setSelectedItem(item: Any?) {
        if (item == null) return

        check(item is JpsVersionItem) { "$item is supposed to be JpsVersionItem" }

        if (!item.myEnabled) {
            return
        }
        super.setSelectedItem(item)
    }
}
