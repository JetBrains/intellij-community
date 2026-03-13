// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.compilerPreferences.configuration

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import com.intellij.util.ui.ThreeStateCheckBox
import org.jetbrains.kotlin.config.VersionView
import org.jetbrains.kotlin.idea.base.compilerPreferences.KotlinBaseCompilerConfigurationUiBundle
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JTextField

internal class KotlinCompilerConfigurableUi(private val onCopyRuntimeFilesCheckBoxChange: Runnable) {

    @JvmField
    val jpsPluginComboBoxModel = ComboBoxModelWithPossiblyDisabledItems()

    lateinit var warningLabel: JLabel
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
    val copyRuntimeFilesCheckBox = object : ThreeStateCheckBox(KotlinBaseCompilerConfigurationUiBundle.message("kotlin.compiler.js.option.output.copy.files")) {
        override fun setState(state: State?) {
            super.setState(state)
            onCopyRuntimeFilesCheckBoxChange.run()
        }
    }
    lateinit var keepAliveCheckBox: ThreeStateCheckBox
    lateinit var enableIncrementalCompilationForJvmCheckBox: JCheckBox
    lateinit var enableIncrementalCompilationForJsCheckBox: JCheckBox
    lateinit var moduleKindComboBox: ComboBox<String>
    lateinit var jvmVersionComboBox: ComboBox<String>
    lateinit var sourceMapPrefix: JTextField
    lateinit var sourceMapEmbedSources: ComboBox<String>

    lateinit var k2jvmGroup: Row
    lateinit var k2jsGroup: Row
    lateinit var kotlinJpsPluginVersionRow: Row
    lateinit var outputDirectoryRow: Row

    @JvmField
    val panel = panel {
        row {
            warningLabel = label("")
                .applyToComponent { icon = AllIcons.General.WarningDialog }
                .visible(false)
                .component
        }

        row {
            reportWarningsCheckBox =
                threeStateCheckBox(KotlinBaseCompilerConfigurationUiBundle.message("kotlin.compiler.option.generate.no.warnings"))
                    .component
        }

        kotlinJpsPluginVersionRow = row(KotlinBaseCompilerConfigurationUiBundle.message("kotlin.compiler.version")) {
            kotlinJpsPluginVersionComboBox = comboBox(jpsPluginComboBoxModel)
                .align(AlignX.FILL)
                .component
        }

        row(KotlinBaseCompilerConfigurationUiBundle.message("language.version")) {
            // Explicit use of MutableCollectionComboBoxModel guarantees that setSelectedItem() can make safe cast.
            languageVersionComboBox = comboBox(MutableCollectionComboBoxModel<VersionView>())
                .align(AlignX.FILL)
                .component
        }

        row(KotlinBaseCompilerConfigurationUiBundle.message("api.version")) {
            // Explicit use of MutableCollectionComboBoxModel guarantees that setSelectedItem() can make safe cast.
            apiVersionComboBox = comboBox(MutableCollectionComboBoxModel<VersionView>())
                .align(AlignX.FILL)
                .component
        }

        row(KotlinBaseCompilerConfigurationUiBundle.message("kotlin.compiler.option.additional.command.line.parameters")) {
            additionalArgsOptionsField = cell(RawCommandLineEditor())
                .align(AlignX.FILL)
                .component
        }

        row {
            keepAliveCheckBox =
                threeStateCheckBox(KotlinBaseCompilerConfigurationUiBundle.message("keep.compiler.process.alive.between.invocations"))
                    .component
        }

        k2jvmGroup = group(KotlinBaseCompilerConfigurationUiBundle.message("kotlin.compiler.jvm.option.panel.title")) {
            row {
                enableIncrementalCompilationForJvmCheckBox = checkBox(
                    KotlinBaseCompilerConfigurationUiBundle.message("enable.incremental.compilation")
                ).component
            }
            row(KotlinBaseCompilerConfigurationUiBundle.message("target.jvm.version")) {
                jvmVersionComboBox = comboBox(emptyList<String>())
                    .align(AlignX.FILL)
                    .component
            }
        }

        k2jsGroup = group(KotlinBaseCompilerConfigurationUiBundle.message("kotlin.compiler.js.option.panel.title")) {
            row {
                enableIncrementalCompilationForJsCheckBox =
                    checkBox(KotlinBaseCompilerConfigurationUiBundle.message("enable.incremental.compilation"))
                        .component
            }
            row {
                generateSourceMapsCheckBox =
                    threeStateCheckBox(KotlinBaseCompilerConfigurationUiBundle.message("kotlin.compiler.js.option.generate.sourcemaps"))
                        .component
            }
            row("") {
                sourceMapPrefix = textField()
                    .align(AlignX.FILL)
                    .enabledIf(generateSourceMapsCheckBox.selected)
                    .component
            }
            row(KotlinBaseCompilerConfigurationUiBundle.message("embed.source.code.into.source.map")) {
                sourceMapEmbedSources = comboBox(emptyList<String>())
                    .align(AlignX.FILL)
                    .component
            }
            row {
                cell(copyRuntimeFilesCheckBox)
            }
            indent {
                outputDirectoryRow = row(KotlinBaseCompilerConfigurationUiBundle.message("destination.directory")) {
                    outputDirectory = cell(TextFieldWithBrowseButton())
                        .align(AlignX.FILL)
                        .applyToComponent {
                            text = KotlinBaseCompilerConfigurationUiBundle.message("kotlin.compiler.lib")
                        }
                        .component
                }
            }
            row(KotlinBaseCompilerConfigurationUiBundle.message("module.kind")) {
                moduleKindComboBox = comboBox(emptyList<String>())
                    .align(AlignX.FILL)
                    .component
            }
        }
    }
}

// Explicit use of MutableCollectionComboBoxModel guarantees that setSelectedItem() can make safe cast.
internal class ComboBoxModelWithPossiblyDisabledItems : MutableCollectionComboBoxModel<JpsVersionItem>() {
    override fun setSelectedItem(item: Any?) {
        if (item == null) return

        check(item is JpsVersionItem) { item.toString() + " is supposed to be JpsVersionItem" }

        if (!item.myEnabled) {
            return
        }
        super.setSelectedItem(item)
    }
}
