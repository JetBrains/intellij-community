// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral")

package com.intellij.dev.core.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.updateSettings.impl.ChannelStatus
import com.intellij.openapi.updateSettings.impl.PatchInfo
import com.intellij.openapi.updateSettings.impl.PlatformUpdateDialog
import com.intellij.openapi.updateSettings.impl.UpdateChannel
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.openapi.updateSettings.impl.UpdateMode
import com.intellij.openapi.updateSettings.impl.debugUpdateMode
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.compose.swing.components.ActionLink
import com.intellij.platform.compose.swing.components.ComboBox
import com.intellij.platform.compose.swing.components.Comment
import com.intellij.platform.compose.swing.components.SegmentedButton
import com.intellij.platform.compose.swing.composeSwingPanel
import com.intellij.platform.compose.swing.components.style.actionLinkIcon
import com.intellij.platform.eel.fs.EelFiles
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.util.containers.nullize
import com.intellij.util.system.CpuArch
import com.intellij.util.text.nullize
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingUndoUtil
import org.jetbrains.compose.swing.animation.AnimatedVisibility
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.expandVertically
import org.jetbrains.compose.swing.animation.shrinkVertically
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.layout.Spacer
import org.jetbrains.compose.swing.components.layout.TabbedPane
import org.jetbrains.compose.swing.components.layout.TabbedPaneScope
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.RowScope
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.widthIn
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.ContainerStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.accessibleName
import org.jetbrains.compose.swing.style.emptyBorder
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.font
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.style.toolTip
import org.jetbrains.compose.swing.tooling.Preview
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JScrollPane

/**
 * @author gregsh
 */
internal class ShowUpdateInfoDialogAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val dialog = MyDialog(project)
    if (dialog.showAndGet()) {
      try {
        when (dialog.mode) {
          MyDialog.Mode.GENERAL ->
            UpdateChecker.testPlatformUpdate(
              project,
              dialog.updateXmlText(),
              dialog.patchFilePath()?.let { Path.of(FileUtil.toSystemDependentName(it)) },
              dialog.forceUpdate,
            )

          MyDialog.Mode.QUICK_MOCK -> dialog.quickMock.execute(project)
          MyDialog.Mode.GENERATE_XML -> Unit
        }
      }
      catch (ex: Exception) {
        Messages.showErrorDialog(project, "${ex.javaClass.name}: ${ex.message}", "Something Went Wrong")
      }
    }
  }

  private class MyDialog(private val project: Project?) : DialogWrapper(project, true) {

    /** The tabs of the dialog, in the order they are shown. */
    enum class Mode {
      GENERAL,
      QUICK_MOCK,
      GENERATE_XML,
    }

    private var selectedTab by mutableIntStateOf(0)

    val mode: Mode
      get() = Mode.entries.getOrElse(selectedTab) { Mode.GENERAL }

    var forceUpdate by mutableStateOf(false)
      private set

    val quickMock = QuickMockState()

    // These stay Swing components. The validation, the initial focus and the undo support need the text area itself.
    // Compose has no counterpart yet for the browse button and the path completion of the file field.
    private val textArea = JBTextArea().apply {
      SwingUndoUtil.addUndoRedoActions(this)
      wrapStyleWord = true
      lineWrap = true
      rows = 30
      columns = 80
    }
    private val fileField = FileChooserFactory.getInstance().createFileTextField(FileChooserDescriptorFactory.singleFile(), disposable)
    private val fileCombo = TextFieldWithBrowseButton(fileField.field).apply {
      addBrowseFolderListener(project, FileChooserDescriptorFactory.singleFile().withTitle("Patch File").withDescription("Patch file"))
    }

    private val okAction = object : AbstractAction(CommonBundle.getOkButtonText()) {

      init {
        putValue(DEFAULT_ACTION, true)
      }

      // Only the General tab has something to validate, see doValidate.
      override fun actionPerformed(e: ActionEvent?) = validateAndDoOkAction()
    }

    init {
      @Suppress("DialogTitleCapitalization")
      title = "Test update dialog"
      init()
    }

    override fun createCenterPanel(): JComponent = composeSwingPanel(disposable) {
      val build = ApplicationInfo.getInstance().build
      UpdateInfoTabs(
        selectedTab = selectedTab,
        onSelectedTabChange = { selectedTab = it },
        textArea = textArea,
        fileCombo = fileCombo,
        forceUpdate = forceUpdate,
        onForceUpdateChange = { forceUpdate = it },
        quickMock = quickMock,
        productCode = build.productCode,
        productName = ApplicationNamesInfo.getInstance().fullProductName,
        currentBuild = build.asStringWithoutProductCode(),
      )
    }

    override fun getOKAction(): Action = okAction

    private fun validateAndDoOkAction() {
      val info = doValidate()
      if (info != null) {
        IdeFocusManager.getInstance(null).requestFocus(textArea, true)
        updateErrorInfo(listOf(info))
        startTrackingValidation()
      }
      else {
        doOKAction()
      }
    }

    override fun doValidate(): ValidationInfo? {
      if (mode != Mode.GENERAL) {
        return null
      }

      val text = getXmlText()
      if (text.isEmpty()) {
        return ValidationInfo("Please paste something here or choose a patch file", textArea).withOKEnabled()
      }

      try {
        JDOMUtil.load(completeUpdateInfoXml(text))
      }
      catch (e: Exception) {
        return ValidationInfo(e.message ?: "Error: ${e.javaClass.name}", textArea).withOKEnabled()
      }

      return super.doValidate()
    }

    override fun getPreferredFocusedComponent() = textArea
    override fun getDimensionServiceKey() = "TEST_UPDATE_INFO_DIALOG"

    fun updateXmlText() = completeUpdateInfoXml(getXmlText())
    fun patchFilePath() = fileField.field.text.nullize(nullizeSpaces = true)

    private fun completeUpdateInfoXml(text: String) =
      when (JDOMUtil.load(text).name) {
        "products" -> text
        "channel" -> {
          val productName = ApplicationNamesInfo.getInstance().fullProductName
          val productCode = ApplicationInfo.getInstance().build.productCode
          """<products><product name="${productName}"><code>${productCode}</code>${text}</product></products>"""
        }
        else -> throw IllegalArgumentException("Unknown root element")
      }

    private fun getXmlText(): String {
      val text = textArea.text.trim()
      if (text.isNotEmpty()) return text

      return patchFilePath()?.let(::xmlTextForPatchUpdate).orEmpty()
    }

    private fun xmlTextForPatchUpdate(path: String) = """
      <channel id="">
        <build number="1" version="fake version">
          <message><![CDATA[Test text for the update dialog<br><br>Selected patch path:<br>$path]]></message>
        </build>
      </channel>""".trimIndent()
  }
}

// region Tabs

/** The tabs of the dialog: one tab for each [ShowUpdateInfoDialogAction.MyDialog.Mode], in the same order. */
@Composable
private fun UpdateInfoTabs(
  selectedTab: Int,
  onSelectedTabChange: (Int) -> Unit,
  textArea: JBTextArea,
  fileCombo: TextFieldWithBrowseButton,
  forceUpdate: Boolean,
  onForceUpdateChange: (Boolean) -> Unit,
  quickMock: QuickMockState,
  productCode: String,
  productName: String,
  currentBuild: String,
) {
  val generateXml = remember { GenerateXmlState(productCode, productName) }
  TabbedPane(selectedIndex = selectedTab, onSelectedIndexChange = onSelectedTabChange) {
    Tab("General") {
      GeneralTab(textArea = textArea, fileCombo = fileCombo, forceUpdate = forceUpdate, onForceUpdateChange = onForceUpdateChange)
    }
    Tab("Quick Mock") {
      QuickMockTab(quickMock)
    }
    Tab("Generate XML") {
      GenerateXmlTab(generateXml, currentProductCode = productCode, currentBuild = currentBuild)
    }
  }
}

/**
 * One tab. A column holds the rows of the tab and fills the tab width. The column scrolls vertically when the tab is too short
 * for it. It never scrolls horizontally.
 */
@Composable
private fun TabbedPaneScope.Tab(title: String, content: @Composable ColumnScope.() -> Unit) {
  val gap = spacing.verticalSmallGap.dp
  ScrollPane(
    SwingModifier.tab(title),
    style = { emptyBorder(0, 0, 0, 0) },
    horizontalScrollbar = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
  ) {
    Viewport {
      Column(
        SwingModifier.styleable { emptyBorder(gap, gap, gap, gap) },
        verticalArrangement = Arrangement.spacedBy(spacing.verticalComponentGap.dp),
        content = content,
      )
    }
  }
}

@Composable
private fun ColumnScope.GeneralTab(
  textArea: JBTextArea,
  fileCombo: TextFieldWithBrowseButton,
  forceUpdate: Boolean,
  onForceUpdateChange: (Boolean) -> Unit,
) {
  Label("Add updates.xml content or choose a patch file", style = { font(JBFont.label().asBold()) })

  Label("Updates.xml <channel> text:")
  ScrollPane(SwingModifier.fillMaxWidth().weight(1f).shrinkableSize(height = 150)) {
    Viewport { ChannelTextArea(textArea) }
  }

  ControlsRow {
    Label("Patch file:")
    PatchFileField(fileCombo, SwingModifier.weight(1f))
  }

  ControlsRow(fillWidth = false) {
    Label("Action:")
    SegmentedButton(
      items = listOf(false, true),
      selectedItem = forceUpdate,
      onSelectedItemChange = { if (it != null) onForceUpdateChange(it) },
      renderer = { showDialog -> if (showDialog) "Show dialog" else "Check updates" },
    )
  }
}

/** Shows [textArea], which the dialog owns and reads when the user clicks OK. */
@Composable
private fun ChannelTextArea(
  textArea: JBTextArea,
  modifier: SwingModifier = SwingModifier,
  style: SwingStyle<ContainerStyleScope> = SwingStyle,
) {
  SwingNode(factory = { textArea }, modifier = modifier.styleable(style))
}

/** Shows [field], which the dialog owns and reads when the user clicks OK. */
@Composable
private fun PatchFileField(
  field: TextFieldWithBrowseButton,
  modifier: SwingModifier = SwingModifier,
  style: SwingStyle<ContainerStyleScope> = SwingStyle,
) {
  SwingNode(factory = { field }, modifier = modifier.styleable(style))
}

/** The state of the Quick Mock tab. [execute] reads it when the user clicks OK. */
@Stable
private class QuickMockState {
  var xml by mutableStateOf(QUICK_MODE_XML)
  var writeProtected by mutableStateOf(false)
  var addConfigLink by mutableStateOf(true)
  var incompatiblePluginsEnabled by mutableStateOf(true)
  var incompatiblePlugins by mutableStateOf("Gradle, Maven, HTML Tools")
  var licenseNote by mutableStateOf("The new version has an expiration date and does not require a license")
  var licenseNoteWarning by mutableStateOf(true)
  var updateMode by mutableStateOf(debugUpdateMode)

  fun execute(project: Project) {
    debugUpdateMode = updateMode

    val loaded = UpdateChecker.testLoadFromXml(xml)
    val incompatiblePlugins = (if (incompatiblePluginsEnabled) incompatiblePlugins else "")
      .split(",")
      .map { it.trim() }
      .filter { it.isNotBlank() }
      .nullize()

    PlatformUpdateDialog.createTestDialog(
      project,
      loaded,
      writeProtected,
      licenseNote.trim().nullize(),
      licenseNoteWarning,
      addConfigLink,
      null,
      incompatiblePlugins,
    ).show()
  }
}

@Composable
private fun ColumnScope.QuickMockTab(state: QuickMockState) {
  ScrollPane(SwingModifier.fillMaxWidth().weight(1f).shrinkableSize(height = 150)) {
    Viewport {
      TextArea(
        value = state.xml,
        onValueChange = { state.xml = it },
        lineWrap = true,
        wrapStyleWord = true,
      )
    }
  }

  ControlsRow(fillWidth = false) {
    CheckBox(text = "Update dir write is protected", checked = state.writeProtected, onCheckedChange = { state.writeProtected = it })
    CheckBox(text = "Add config link", checked = state.addConfigLink, onCheckedChange = { state.addConfigLink = it })
  }

  ControlsRow {
    CheckBox(
      text = "Incompatible plugins:",
      checked = state.incompatiblePluginsEnabled,
      onCheckedChange = { state.incompatiblePluginsEnabled = it },
    )
    TextField(
      value = state.incompatiblePlugins,
      onValueChange = { state.incompatiblePlugins = it },
      modifier = SwingModifier.weight(1f),
      style = { enabled(state.incompatiblePluginsEnabled) },
      columns = 1,
    )
  }

  ControlsRow {
    CheckBox(text = "License warning:", checked = state.licenseNoteWarning, onCheckedChange = { state.licenseNoteWarning = it })
    TextField(
      value = state.licenseNote,
      onValueChange = { state.licenseNote = it },
      modifier = SwingModifier.weight(1f),
      style = { enabled(state.licenseNoteWarning) },
      columns = 1,
    )
  }

  ControlsRow(fillWidth = false) {
    Label("Update mode:")
    ComboBox(
      items = UPDATE_MODE_ITEMS,
      selectedItem = UpdateModeItem(state.updateMode),
      onSelectedItemChange = { state.updateMode = it?.mode },
    )
  }
  Comment("Kept after this dialog closes, reset after a restart. Affects the update dialog and the IDE update toolbar widget")
}

/** An item of the update mode combo box. The combo box cannot hold `null`, so an item with a `null` [mode] stands for no mode. */
private data class UpdateModeItem(val mode: UpdateMode?) {
  override fun toString(): String = mode?.name ?: "< none >"
}

private val UPDATE_MODE_ITEMS: List<UpdateModeItem> = listOf(UpdateModeItem(null)) + UpdateMode.entries.map(::UpdateModeItem)

// endregion

// region Generate XML

/**
 * The channels that the update server publishes for every product, as in https://www.jetbrains.com/updates/updates.xml.
 * The server no longer publishes the milestone and beta statuses of [ChannelStatus], so this list does not offer them.
 */
private enum class UpdateChannelKind(
  private val displayName: String,
  val status: ChannelStatus,
  val licensing: UpdateChannel.Licensing,
  val description: String,
) {
  RELEASE(
    displayName = "Release",
    status = ChannelStatus.RELEASE,
    licensing = UpdateChannel.Licensing.RELEASE,
    description = "Stable releases; offered to every IDE.",
  ),
  EAP(
    displayName = "EAP",
    status = ChannelStatus.EAP,
    licensing = UpdateChannel.Licensing.EAP,
    description = "Early Access builds, which run without a license; offered only to IDEs subscribed to EAP updates.",
  ),
  EAP_RELEASE_LICENSING(
    displayName = "EAP with release licensing",
    status = ChannelStatus.EAP,
    licensing = UpdateChannel.Licensing.RELEASE,
    description = "Early Access builds that still check the license, such as release candidates.",
  );

  /** The id the server gives the channel, e.g. `IU-EAP-licensing-RELEASE`. */
  fun channelId(productCode: String): String = "$productCode-${status.name}-licensing-${licensing.name}"

  /** The name the server gives the channel, e.g. "IntelliJ IDEA EAP licensing:RELEASE". */
  fun channelName(productName: String): String = when (this) {
    EAP_RELEASE_LICENSING -> "$productName ${status.name} licensing:${licensing.name}"
    RELEASE, EAP -> "$productName ${status.name}"
  }

  override fun toString(): String = displayName
}

/**
 * The name of a patch file that the IDE downloads from `idea.patches.url`, e.g. `IU-262.9437-263.1234-patch-aarch64-mac.jar`.
 * See `BaseJetBrainsExternalProductResourceUrls.computePatchUrl`.
 */
private data class PatchName(val productCode: String, val from: String, val to: String, val aarch64: Boolean, val os: String) {
  override fun toString(): String = "$productCode-$from-$to-patch${if (aarch64) "-aarch64" else ""}-$os.jar"

  companion object {
    const val FORMAT: String = "product-from-to-patch[-aarch64]-os.jar"

    // A product code and a build number have no dashes, so the dashes separate the parts.
    private val PATTERN = Regex("""([^-]*)-([^-]*)-([^-]*)-patch(-aarch64)?-([^-.]+)(\.jar)?""")

    fun parse(text: String): PatchName? {
      val match = PATTERN.matchEntire(text.trim()) ?: return null
      val (productCode, from, to, aarch64, os) = match.destructured
      return PatchName(productCode, from, to, aarch64.isNotEmpty(), os)
    }
  }
}

/** The text that the user typed into the patch name field. The field shows it while the other fields still make [basis]. */
private data class PatchNameDraft(val text: String, val basis: String)

/** The location of the saved XML and patch directory, and the VM options that make an IDE update from there. */
private class SavedUpdates(val xmlFile: Path, val patchDirectory: Path) {
  val vmOptions: String = """
    -Didea.updates.url=${xmlFile.toUri()}
    -Didea.patches.url=${patchDirectory.toUri()}
  """.trimIndent()

  companion object {
    @Throws(IOException::class)
    fun save(xml: String): SavedUpdates {
      val directory = Files.createTempDirectory("updates-")
      val xmlFile = directory.resolve("updates.xml")
      EelFiles.writeString(xmlFile, xml)
      return SavedUpdates(xmlFile, Files.createDirectories(directory.resolve("patches")))
    }
  }
}

/** The state of the Generate XML tab: the fields, the generated XML, and the result of the last save. */
@Stable
private class GenerateXmlState(productCode: String, productName: String) {
  var productCode by mutableStateOf(productCode)
  var productName by mutableStateOf(productName)
  var buildNumber by mutableStateOf("")
  var buildVersion by mutableStateOf("")
  var patchFrom by mutableStateOf("")
  var channel by mutableStateOf(UpdateChannelKind.RELEASE)
  var message by mutableStateOf("")
  var xml by mutableStateOf("")

  var saved by mutableStateOf<SavedUpdates?>(null)
    private set
  var saveError by mutableStateOf<String?>(null)
    private set

  // The patch is for the current machine, unless the user types a patch name for a different one.
  private var patchAarch64 by mutableStateOf(CpuArch.isArm64())
  private var patchOs by mutableStateOf(PatchInfo.OS_SUFFIX)
  private var patchNameDraft by mutableStateOf<PatchNameDraft?>(null)

  val requiredFilled: Boolean
    get() = productCode.isNotBlank() && productName.isNotBlank() && buildNumber.isNotBlank()

  private val patchName: String
    get() = PatchName(productCode, patchFrom, buildNumber, patchAarch64, patchOs).toString()

  /**
   * The text of the patch name field. It is the draft of the user while the fields still make the draft basis.
   * Otherwise, it is the name that the fields make.
   */
  val patchNameText: String
    get() = patchNameDraft?.takeIf { it.basis == patchName }?.text ?: patchName

  /** Sets the fields from [text] if it is a valid patch name. Keeps [text] as a draft if it differs from the name the fields make. */
  fun editPatchName(text: String) {
    val parsed = PatchName.parse(text)
    if (parsed != null) {
      productCode = parsed.productCode
      patchFrom = parsed.from
      buildNumber = parsed.to
      patchAarch64 = parsed.aarch64
      patchOs = parsed.os
    }
    val basis = parsed?.toString() ?: patchName
    patchNameDraft = if (basis == text) null else PatchNameDraft(text, basis)
  }

  fun generate() {
    xml = generateUpdatesXml(productCode, productName, buildNumber, buildVersion, patchFrom, message, channel)
  }

  /** Generates the XML from the current fields and saves it. This replaces the XML that the user edited by hand. */
  fun saveToTempDirectory() {
    generate()
    try {
      saved = SavedUpdates.save(xml)
      saveError = null
    }
    catch (e: IOException) {
      saved = null
      saveError = e.message ?: e.javaClass.name
    }
  }
}

@Composable
private fun ColumnScope.GenerateXmlTab(state: GenerateXmlState, currentProductCode: String, currentBuild: String) {
  var showDescriptions by remember { mutableStateOf(false) }
  fun describe(text: String): String? = text.takeIf { showDescriptions }

  PatchNameInput(state)

  ControlsRow {
    Comment("Fields marked with * are required", modifier = SwingModifier.weight(1f))
    CheckBox(checked = showDescriptions, onCheckedChange = { showDescriptions = it }, text = "Show descriptions")
  }

  Row(SwingModifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.horizontalDefaultGap.dp)) {
    TextInput(
      label = "Product code",
      value = state.productCode,
      onValueChange = { state.productCode = it },
      modifier = SwingModifier.weight(1f),
      description = describe("Only the product whose code matches the running IDE ($currentProductCode) is read."),
      required = true,
    )
    TextInput(
      label = "Product name",
      value = state.productName,
      onValueChange = { state.productName = it },
      modifier = SwingModifier.weight(1f),
      description = describe("Shown as the product name in the update dialog."),
      required = true,
    )
  }
  Row(SwingModifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.horizontalDefaultGap.dp)) {
    TextInput(
      label = "Build number",
      value = state.buildNumber,
      onValueChange = { state.buildNumber = it },
      modifier = SwingModifier.weight(1f),
      description = describe("The offered build, e.g. 263.1234.56. Offered only when newer than the running build, $currentBuild."),
      required = true,
    )
    TextInput(
      label = "Build version",
      value = state.buildVersion,
      onValueChange = { state.buildVersion = it },
      modifier = SwingModifier.weight(1f),
      description = describe("The version the user sees, e.g. 2026.3.1."),
    )
  }
  Row(SwingModifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.horizontalDefaultGap.dp)) {
    TextInput(
      label = "Patch from",
      value = state.patchFrom,
      onValueChange = { state.patchFrom = it },
      modifier = SwingModifier.weight(1f),
      description = describe(
        "The build a patch updates from, usually the running one, $currentBuild. Leave empty to offer only a full download."
      ),
    )
    Input(label = "Channel", modifier = SwingModifier.weight(1f), description = describe(state.channel.description)) {
      ComboBox(
        items = UpdateChannelKind.entries,
        selectedItem = state.channel,
        onSelectedItemChange = { if (it != null) state.channel = it },
        modifier = SwingModifier.fillMaxWidth(),
      )
    }
  }

  Input(label = "Message", modifier = SwingModifier.fillMaxWidth(), labelComment = "Announcement shown in the update dialog") {
    ScrollPane(SwingModifier.fillMaxWidth().shrinkableSize(height = 60)) {
      Viewport {
        TextArea(
          value = state.message,
          onValueChange = { state.message = it },
          lineWrap = true,
          wrapStyleWord = true,
        )
      }
    }
  }

  ScrollPane(SwingModifier.fillMaxWidth().weight(1f).shrinkableSize(height = 120)) {
    Viewport { TextArea(value = state.xml, onValueChange = { state.xml = it }) }
  }

  ControlsRow {
    Button(
      text = "Save to temp directory",
      onClick = state::saveToTempDirectory,
      style = { enabled(state.requiredFilled) },
    )
    Button(text = "Generate XML", onClick = state::generate, style = { enabled(state.requiredFilled) })
  }

  SavedUpdatesInfo(state.saved, state.saveError, describe = ::describe)
}

@Composable
private fun ColumnScope.PatchNameInput(state: GenerateXmlState) {
  Input(
    label = "Patch file name",
    modifier = SwingModifier.fillMaxWidth(),
    description = "Not a valid patch file name: ${PatchName.FORMAT}".takeIf { PatchName.parse(state.patchNameText) == null },
    labelComment = "The patch file the IDE downloads from the patch directory.",
  ) {
    TextField(
      value = state.patchNameText,
      onValueChange = state::editPatchName,
      modifier = SwingModifier.fillMaxWidth(),
      columns = 1,
    )
  }
}

/** The paths and the VM options of [saved], or the [saveError] of the last save. */
@Composable
private fun ColumnScope.SavedUpdatesInfo(saved: SavedUpdates?, saveError: String?, describe: (String) -> String?) {
  Row(SwingModifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.horizontalDefaultGap.dp)) {
    PathInfo(
      label = "XML file",
      path = saved?.xmlFile,
      modifier = SwingModifier.weight(1f),
      description = describe("Where the generated XML was saved."),
    )
    PathInfo(
      label = "Patch directory",
      path = saved?.patchDirectory,
      modifier = SwingModifier.weight(1f),
      description = describe("Put the patch files the XML names here."),
      directory = true,
    )
  }
  Input(
    label = "VM options",
    modifier = SwingModifier.fillMaxWidth(),
    description = describe("These options set where the IDE looks for updates and patches."),
    labelComment = saveError?.let { "Could not save: $it" } ?: "Add these options to the IDE to test the update flow",
  ) {
    ControlsRow {
      ScrollPane(SwingModifier.weight(1f).shrinkableSize(height = 50)) {
        // The text wraps at any character, because the URLs have no spaces.
        Viewport {
          TextArea(
            value = saved?.vmOptions.orEmpty(),
            onValueChange = {},
            editable = false,
            lineWrap = true,
          )
        }
      }
      if (saved != null) {
        CopyLink(saved.vmOptions, tooltip = "Copy VM options")
      }
    }
  }
}

private fun generateUpdatesXml(
  productCode: String,
  productName: String,
  buildNumber: String,
  buildVersion: String,
  patchFrom: String,
  message: String,
  channel: UpdateChannelKind,
): String {
  fun attr(value: String) = StringUtil.escapeXmlEntities(value)

  val status = channel.status.code
  val licensing = StringUtil.toLowerCase(channel.licensing.name)
  val channelId = attr(channel.channelId(productCode))
  val channelName = attr(channel.channelName(productName))
  val versionAttr = if (buildVersion.isNotBlank()) """ version="${attr(buildVersion)}"""" else ""
  return buildString {
    appendLine("""<?xml version="1.0"?>""")
    appendLine("""<products>""")
    appendLine("""  <product name="${attr(productName)}">""")
    appendLine("""    <code>${attr(productCode)}</code>""")
    appendLine("""    <channel id="$channelId" name="$channelName" status="$status" licensing="$licensing">""")
    appendLine("""      <build number="${attr(buildNumber)}"$versionAttr>""")
    appendLine("""        <message><![CDATA[$message]]></message>""")
    if (patchFrom.isNotBlank()) {
      appendLine("""        <patch from="${attr(patchFrom)}"/>""")
    }
    appendLine("""      </build>""")
    appendLine("""    </channel>""")
    appendLine("""  </product>""")
    append("""</products>""")
  }
}

// endregion

// region Inputs

private val spacing = IntelliJSpacingConfiguration()

/** Puts the controls of one row side by side, with the gap between related controls, such as a label and its field. */
@Composable
private fun ColumnScope.ControlsRow(fillWidth: Boolean = true, content: @Composable RowScope.() -> Unit) {
  Row(
    if (fillWidth) SwingModifier.fillMaxWidth() else SwingModifier,
    horizontalArrangement = Arrangement.spacedBy(spacing.horizontalSmallGap.dp),
    verticalAlignment = Alignment.CenterVertically,
    content = content,
  )
}

/**
 * Sets a small preferred size. A scroll pane asks for the width of its longest line, which widens the dialog. With this size, it
 * takes the width that its parent gives. Its height is [height] until the parent gives more.
 */
private fun SwingModifier.shrinkableSize(height: Int): SwingModifier = styleable { preferredSize(100.dp, height.dp) }

@Composable
private fun Input(
  label: String,
  modifier: SwingModifier = SwingModifier,
  description: String? = null,
  labelComment: String? = null,
  required: Boolean = false,
  content: @Composable ColumnScope.() -> Unit,
) {
  var shownDescription by remember { mutableStateOf(description.orEmpty()) }
  if (description != null) {
    shownDescription = description
  }

  Column(modifier) {
    val title = if (required) "$label *" else label
    if (labelComment == null) {
      Label(title)
    }
    else {
      ControlsRow(fillWidth = false) {
        Label(title)
        Comment(labelComment)
      }
    }
    Spacer(spacing.verticalComponentGap.dp)
    content()
    // A comment asks for the width of its longest line, which widens the dialog. With no width, it wraps to the column width.
    AnimatedVisibility(
      visible = description != null,
      modifier = SwingModifier.fillMaxWidth().widthIn(max = 0),
      enter = expandVertically(tween(100)),
      exit = shrinkVertically(tween(100)),
    ) {
      Spacer(spacing.verticalComponentGap.dp)
      Comment(shownDescription, modifier = SwingModifier.fillMaxWidth())
    }
  }
}

@Composable
private fun TextInput(
  label: String,
  value: String,
  onValueChange: (String) -> Unit,
  modifier: SwingModifier = SwingModifier,
  description: String? = null,
  required: Boolean = false,
) {
  Input(label, modifier, description, required = required) {
    // With one column, the field asks for almost no width. Two fields side by side then share the row evenly.
    TextField(value = value, onValueChange = onValueChange, modifier = SwingModifier.fillMaxWidth(), columns = 1)
  }
}

/**
 * Shows [path] as plain text, with an icon that copies it. For a [directory], it also shows an icon that opens it in the file
 * manager, if the system has one.
 */
@Composable
private fun PathInfo(
  label: String,
  path: Path?,
  modifier: SwingModifier = SwingModifier,
  description: String? = null,
  directory: Boolean = false,
) {
  Input(label, modifier, description, labelComment = if (path == null) "Not saved yet" else null) {
    if (path != null) {
      ControlsRow {
        // A label asks for the width of its text. With no width, it fits its column and cuts a long path with an ellipsis.
        val text = path.toString()
        val lineHeight = remember { JLabel(" ").preferredSize.height }
        Label(
          text,
          modifier = SwingModifier.weight(1f),
          style = {
            preferredSize(0, lineHeight)
            toolTip(text)
          },
        )
        CopyLink(text, tooltip = "Copy path")
        if (directory && RevealFileAction.isDirectoryOpenSupported()) {
          IconLink(AllIcons.Actions.MenuOpen, tooltip = RevealFileAction.getActionName()) { RevealFileAction.openDirectory(path) }
        }
      }
    }
  }
}

@Composable
private fun CopyLink(text: String, tooltip: String) {
  IconLink(AllIcons.Actions.Copy, tooltip) { CopyPasteManager.getInstance().setContents(StringSelection(text)) }
}

@Composable
private fun IconLink(icon: Icon, tooltip: String, onClick: () -> Unit) {
  ActionLink(
    text = "",
    onClick = onClick,
    style = {
      actionLinkIcon(icon)
      toolTip(tooltip)
      accessibleName(tooltip)
    },
  )
}

// endregion

private val Int.dp: Int get() = JBUI.scale(this)

@Preview(width = 700)
@Composable
private fun ShowUpdateInfoDialogPreview() {
  var selectedTab by remember { mutableIntStateOf(0) }
  val textArea = remember { JBTextArea(10, 80).apply { lineWrap = true; wrapStyleWord = true } }
  val fileCombo = remember { TextFieldWithBrowseButton() }
  var forceUpdate by remember { mutableStateOf(false) }
  val quickMock = remember { QuickMockState() }

  UpdateInfoTabs(
    selectedTab = selectedTab,
    onSelectedTabChange = { selectedTab = it },
    textArea = textArea,
    fileCombo = fileCombo,
    forceUpdate = forceUpdate,
    onForceUpdateChange = { forceUpdate = it },
    quickMock = quickMock,
    productCode = "IU",
    productName = "IntelliJ IDEA",
    currentBuild = "262.1",
  )
}

private const val QUICK_MODE_XML = """
<products>
  <product name="IntelliJ IDEA">
    <code>IU</code>
    <channel id="IU-RELEASE-licensing-RELEASE" name="IntelliJ IDEA RELEASE" status="release" url="https://www.jetbrains.com/idea/download" feedback="https://youtrack.jetbrains.com/issues/IDEA" majorVersion="2027" licensing="release">
      <build number="371.9999" version="2037.1.1" releaseDate="20360101" fullNumber="371.9999.123">
        <blogPost url="https://blog.jetbrains.com/idea/2026/08/intellij-idea-2026-2-1/"/>
        <message><![CDATA[<p>IntelliJ IDEA 2037.1.1 is out with the following improvements:</p>
<ul>
 <li>Markdown shell scripts now execute in the correct order. [<a href="https://youtrack.jetbrains.com/issue/IJPL-92206/">IJPL-92206</a>]</li>
 <li>Undo now works correctly after applying <em>Optimize imports on the fly</em>. [<a href="https://youtrack.jetbrains.com/issue/IDEA-285011/">IDEA-285011</a>]</li>
 <li>Dragging a terminal tab after using the <em>Move to Editor</em> action no longer restarts the terminal session. [<a href="https://youtrack.jetbrains.com/issue/IJPL-165734/Terminal-restarts-when-dragged-after-Move-to-Editor">IJPL-165734</a>]</li>
</ul>
<p>Get more details in our <a href="https://blog.jetbrains.com/idea/2026/08/intellij-idea-2026-2-1/">blog post</a>.</p>]]></message>
        <button name="Download" url="https://www.jetbrains.com/idea/download" download="true"/>
        <button name="Release Notes" url="https://youtrack.jetbrains.com/articles/IDEA-A-2100662729"/>
        <button name="More Information" url="https://blog.jetbrains.com/idea/2026/08/intellij-idea-2026-2-1/"/>
        <patch from="262.9437" size="from 15 to 17" fullFrom="262.9437.65"/>
        <patch from="261.26222" size="from 618 to 666" fullFrom="261.26222.65"/>
        <patch from="262.8665" size="from 130 to 163" fullFrom="262.8665.337"/>
      </build>
    </channel>
  </product>
</products>
"""
