package org.jetbrains.completion.full.line.settings.ui

import com.intellij.lang.Language
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.*
import org.jetbrains.completion.full.line.language.*
import org.jetbrains.completion.full.line.models.ModelType
import org.jetbrains.completion.full.line.services.managers.ConfigurableModelsManager
import org.jetbrains.completion.full.line.services.managers.excessLanguage
import org.jetbrains.completion.full.line.services.managers.missedLanguage
import org.jetbrains.completion.full.line.settings.MLServerCompletionBundle.Companion.message
import org.jetbrains.completion.full.line.settings.state.MLServerCompletionSettings
import org.jetbrains.completion.full.line.settings.state.MlServerCompletionAuthState
import org.jetbrains.completion.full.line.settings.ui.components.*
import org.jetbrains.completion.full.line.tasks.SetupLocalModelsTask
import org.jetbrains.completion.full.line.thisLogger
import javax.swing.JTextField
import javax.swing.ListCellRenderer
import kotlin.reflect.KMutableProperty1
import org.jetbrains.completion.full.line.visibleIf

@Suppress("DuplicatedCode")
class FullLineReducedConfigurable : BoundConfigurable(message("fl.server.completion.display")), SearchableConfigurable {
    init {
        // It is necessary to preload the configuration services so that they are not reset
        service<MLServerCompletionSettings>()
        service<MlServerCompletionAuthState>()
    }

    private val logger = thisLogger()

    private val settings = MLServerCompletionSettings.getInstance()
    private val settingsAuth = MlServerCompletionAuthState.getInstance()

    private lateinit var modelType: CellBuilder<ComboBox<ModelType>>
    private lateinit var flccEnabled: ComponentPredicate
    private val langsEnabled = mutableMapOf<String, ComponentPredicate>()

    private val languages = MLServerCompletionSettings.availableLanguages
    private val generalState = settings.state
    private val langStates = languages.sortedBy { it.id }.map { settings.getLangState(it) }
    private val modelStates = languages.sortedBy { it.id }.map { language ->
        ModelType.values().map { settings.getModelState(language, it) }
    }.flatten()

    private val biggestLang = languages.map { it.displayName }.maxByOrNull { it.length }

    override fun createPanel(): DialogPanel {
        return panel {
            titledRow(message("fl.server.completion.settings.group")) {
                row {
                    cell {
                        flccEnabled = checkBox(message("fl.server.completion.display"), generalState::enable).selected
                        if (settingsAuth.isVerified()) {
                            label("(Verified)").component.apply {
                                foreground = JBColor(JBColor.GREEN.darker(), JBColor.GREEN.brighter())
                            }
                        }
                    }
                    row {
                        subRowIndent = 2
                        if (settingsAuth.isVerified()) {
                            internal()
                        } else {
                            community()
                        }
                    }.enableSubRowsIf(flccEnabled)
                }
            }
        }.also { panel ->
            // Unite callbacks for enabling same languages if more than on model typ provided
            if (settingsAuth.isVerified()) {
                with(LinkedHashMap<String, MutableList<JBCheckBox>>()) {
                    panel.languageCheckboxes().groupByTo(this) { it.text }
                    values.filter { it.size >= 2 }.forEach { (cloudState, localState) ->
                        require(cloudState.text == localState.text)

                        langsEnabled[localState.text] = cloudState.selected
                        localState.connectWith(cloudState)
                        cloudState.connectWith(localState)
                    }
                }
            }
        }
    }

    private fun Row.notAvailable(cause: String?) = row {
        logger.info("Settings are not available" + (cause?.let { ", cause: $cause." } ?: ""))
        comment("Currently, Full Line is available only for Python language, please install its plugin and restart or use PyCharm")
    }

    private fun Row.community() {
        logger.info("Using community settings")
        val lang = Language.findLanguageByID("Python")
        if (lang == null) {
            notAvailable("Python plugin is missing")
            return
        }
        if (FullLineLanguageSupporter.getInstance(lang) == null) {
            notAvailable("Python supporter is missing")
            return
        }
        val langState = settings.getLangState(lang)

        row(message("fl.server.completion.enable.languages")) {
            localModels(listOf(lang))
        }
        row(message("fl.server.completion.settings.language")) {
            row {
                val useBox = checkBox(message("fl.server.completion.top.n.use"), generalState::useTopN)
                row { intTextFieldFixed(generalState::topN, 1, IntRange(0, 20)).enableIf(useBox.selected) }
            }
            row {
                cell {
                    label(message("fl.server.completion.ref.check"))
                    comboBox(
                        CollectionComboBoxModel(RedCodePolicy.values().toList()),
                        langState::redCodePolicy,
                        RedCodePolicyRenderer()
                    )
                }
            }
            row {
                cell {
                    checkBox(message("fl.server.completion.enable.strings.walking"), langState::stringsWalking)
                    component(ContextHelpLabel.create(message("fl.server.completion.enable.strings.walking.help")))
                }
            }
            extended {
                row {
                    checkBox(message("fl.server.completion.only.full"), langState::onlyFullLines)
                }
                row {
                    checkBox(message("fl.server.completion.group.answers"), langState::groupAnswers)
                }
                row {
                    checkBox(message("fl.server.completion.score"), langState::showScore)
                }
            }
        }
    }

    private fun Row.internal() {
        logger.info("Using internal settings")
        if (languages.isEmpty()) {
            notAvailable("No supported languages installed")
            return
        }

        row {
            cell {
                label(message("full.line.settings.model.type"))
                modelType = comboBox(CollectionComboBoxModel(ModelType.values().toList()), generalState::modelType).also {
                    it.component.renderer = listCellRenderer { value, _, _ ->
                        text = value.name
                        icon = value.icon
                    }
                }
            }
        }
        row(message("fl.server.completion.enable.languages")) {
            row {
                localModels(languages)
            }.visibleIf(modelType.component.selectedValueIs(ModelType.Local))
            row {
                cloudModels()
            }.visibleIf(modelType.component.selectedValueIs(ModelType.Cloud))
        }

        row(message("fl.server.completion.settings.completion")) {
            row {
                val useBox = checkBox(message("fl.server.completion.top.n.use"), generalState::useTopN)
                row { intTextFieldFixed(generalState::topN, 1, IntRange(0, 20)).enableIf(useBox.selected) }
            }
            row {
                cell {
                    label(message("fl.server.completion.ref.check"))
                    comboBox(
                        CollectionComboBoxModel(RedCodePolicy.values().toList()),
                        LangState::redCodePolicy,
                        RedCodePolicyRenderer()
                    )
                }
            }
            row {
                cell {
                    checkBox(message("fl.server.completion.enable.strings.walking"), LangState::stringsWalking)
                    component(ContextHelpLabel.create(message("fl.server.completion.enable.strings.walking.help")))
                }
            }
            extended {
                row {
                    checkBox(message("fl.server.completion.only.full"), LangState::onlyFullLines)
                }
                row {
                    checkBox(message("fl.server.completion.group.answers"), LangState::groupAnswers)
                }
                row {
                    checkBox(message("fl.server.completion.score"), LangState::showScore)
                }
            }
        }
        extended {
            separatorRow()
            row(message("fl.server.completion.bs")) {
                extended {
                    right {
                        link(message("full.line.settings.reset.default")) {
                            generalState.langStates.keys.forEach {
                                val lang = Language.findLanguageByID(it) ?: return@forEach
                                generalState.langStates[it] = FullLineLanguageSupporter.getInstance(lang)?.langState ?: LangState()
                            }
                        }
                    }
                }
                row(message("fl.server.completion.bs.num.iterations")) {
                    intTextFieldFixed(ModelState::numIterations, 1, IntRange(0, 50))
                }
                row(message("fl.server.completion.bs.beam.size")) {
                    intTextFieldFixed(ModelState::beamSize, 1, IntRange(0, 20))
                }
                row(message("fl.server.completion.bs.len.base")) {
                    doubleTextField(ModelState::lenBase, 1, IntRange(0, 10))
                }
                row(message("fl.server.completion.bs.len.pow")) {
                    doubleTextField(ModelState::lenPow, 1, IntRange(0, 1))
                }
                row(message("fl.server.completion.bs.diversity.strength")) {
                    doubleTextField(ModelState::diversityStrength, 1, IntRange(0, 10))
                }
                row(message("fl.server.completion.bs.diversity.groups")) {
                    intTextFieldFixed(ModelState::diversityGroups, 1, IntRange(0, 5))
                    row {
                        val groupUse = checkBox(
                            message("fl.server.completion.group.top.n.use"),
                            ModelState::useGroupTopN
                        ).selected
                        row {
                            intTextFieldFixed(ModelState::groupTopN, 1, IntRange(0, 20))
                                .enableIf(groupUse)
                        }
                    }
                }
                row {
                    val groupUse = checkBox(message("fl.server.completion.context.length.use"), ModelState::useCustomContextLength).selected
                    row {
                        intTextFieldFixed(ModelState::customContextLength, 1, IntRange(0, 384)).enableIf(groupUse)
                    }
                }
                row(message("fl.server.completion.deduplication.minimum.prefix")) {
                    doubleTextField(ModelState::minimumPrefixDist, 1, IntRange(0, 1))
                }
                row(message("fl.server.completion.deduplication.minimum.edit")) {
                    doubleTextField(ModelState::minimumEditDist, 1, IntRange(0, 1))
                }
                row(message("fl.server.completion.deduplication.keep.kinds")) {
                    cell {
                        KeepKind.values().map { kind ->
                            checkBox(
                                kind.name.toLowerCase().capitalize(),
                                { modelStates.first().keepKinds.contains(kind) },
                                { action ->
                                    modelStates.forEach {
                                        if (action) it.keepKinds.add(kind) else it.keepKinds.remove(kind)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
//        if (language == JavaLanguage.INSTANCE) {
//            row {
//                checkBox(message("fl.server.completion.enable.psi.completion"), state::psiBased)
//            }
//        }
    }

    private fun Row.cloudModels() {
        languages.forEach { language ->
            val checkBox = languageCheckBox(language, biggestLang)
            val loadingIcon = LoadingComponent()

            fullRow {
                component(checkBox)
                    .withSelectedBinding(MLServerCompletionSettings.getInstance().getLangState(language)::enabled.toBinding())
                component(pingButton(language, loadingIcon, null))
                    .enableIf(checkBox.selected)
                loadingStatus(loadingIcon).forEach {
                    // Do not show even, if model type was changed. Visibility will be controlled from LoadingComponent itself
                    it.enableIf(checkBox.selected).visibleIf(modelType.component.selectedValueMatches { false })
                }
            }
        }
    }

    private fun Row.localModels(languages: List<Language>) {
        val actions = mutableListOf<SetupLocalModelsTask.ToDoParams>()

        languages.map { language ->
            row {
                component(languageCheckBox(language, biggestLang)).withSelectedBinding(settings.getLangState(language)::enabled.toBinding())
                extended {
                    row {
                        subRowIndent = 0
                        cell {
                            component(modelFromLocalFileLinkLabel(language, actions))
                            service<ConfigurableModelsManager>().modelsSchema.models
                                .find { it.currentLanguage == language.id.toLowerCase() }
                                ?.let {
                                    commentNoWrap(
                                        message("fl.server.completion.models.source.local.comment", it.version, it.uid())
                                    )
                                }
                        }
                    }
//                    Removed cause deleting now exec after language turn off
//                    row {
//                        component(deleteCurrentModelLinkLabel(language, actions))
//                    }
                }
            }
        }

        var resetCount = 0
        onGlobalReset {
            // Add download actions for languages which are enabled, but not downloaded.
            // resetCount flag used cause reset is called firstly called right after components initialisation
            resetCount++
        }
        onGlobalIsModified {
            settings.getModelMode() == ModelType.Local
                    && ((resetCount <= 1 && languages.any { service<ConfigurableModelsManager>().missedLanguage(it) }) || actions.isNotEmpty())
        }
        onGlobalApply {
            if (settings.getModelMode() == ModelType.Local) {
                val taskActions = mutableListOf<SetupLocalModelsTask.ToDoParams>()

                // Add actions to import local
                taskActions.addAll(actions)
                actions.clear()

                // Add actions to remove local models which are downloaded, but disabled
                taskActions.addAll(
                    languages.filter {
                        service<ConfigurableModelsManager>().excessLanguage(it)
                    }.map {
                        SetupLocalModelsTask.ToDoParams(it, SetupLocalModelsTask.Action.REMOVE)
                    }
                )
                // Add actions to download local models which are removed, but enabled
                taskActions.addAll(
                    languages.filter {
                        service<ConfigurableModelsManager>().missedLanguage(it)
                    }.map {
                        SetupLocalModelsTask.ToDoParams(it, SetupLocalModelsTask.Action.DOWNLOAD)
                    }
                )
                SetupLocalModelsTask.queue(taskActions)
            }
        }
    }

    override fun getHelpTopic() = "full.line.completion.reduced"
    override fun getId() = helpTopic
    override fun getDisplayName() = "Full Line Code Completion"

    // Below listed component wrappers to create settings for all language/modeltype states.
    // Taking value from first language (sorted by id)
    // Setting value for all states at ones

    @JvmName("checkBoxLang")
    private fun Cell.checkBox(text: String, prop: KMutableProperty1<LangState, Boolean>, comment: String? = null): CellBuilder<JBCheckBox> {
        val bindings = langStates.map { prop.toBinding(it) }
        val component = JBCheckBox(text, bindings.first().get())
        return component(comment = comment).withSelectedBinding(bindings)
    }

    @JvmName("checkBoxModel")
    private fun Cell.checkBox(
        text: String,
        prop: KMutableProperty1<ModelState, Boolean>,
        comment: String? = null
    ): CellBuilder<JBCheckBox> {
        val bindings = modelStates.map { prop.toBinding(it) }
        val component = JBCheckBox(text, bindings.first().get())
        return component(comment = comment).withSelectedBinding(bindings)
    }

    private inline fun <reified T : Any> Cell.comboBox(
        model: CollectionComboBoxModel<T>,
        prop: KMutableProperty1<LangState, T>,
        renderer: ListCellRenderer<T?>? = null
    ): CellBuilder<ComboBox<T>> {
        val bindings = langStates.map { prop.toBinding(it) }
        return comboBox(model, bindings.toBind().toNullable(), renderer)
    }

    private fun Cell.intTextFieldFixed(
        prop: KMutableProperty1<ModelState, Int>,
        columns: Int? = null,
        range: IntRange? = null
    ): CellBuilder<JTextField> {
        val bindings = modelStates.map { prop.toBinding(it) }
        return intTextFieldFixed(bindings.toBind(), columns, range)
    }

    private fun Cell.doubleTextField(
        prop: KMutableProperty1<ModelState, Double>,
        columns: Int? = null,
        range: IntRange? = null
    ): CellBuilder<JTextField> {
        val bindings = modelStates.map { prop.toBinding(it) }
        return doubleTextField(bindings.toBind(), columns, range)
    }
}
