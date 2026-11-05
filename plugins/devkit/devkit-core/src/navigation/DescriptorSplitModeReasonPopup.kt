// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation

import com.intellij.devkit.core.icons.DevkitCoreIcons
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorCssFontResolver
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.ColorUtil
import com.intellij.ui.DeferredIcon
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.IconUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.ExtendableHTMLViewFactory
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.DependencyTraceStep
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.KindEvidence
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.ModuleKindReason
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.RPC_MODULE_NAME
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService.ModuleKind
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeModuleKindIcons
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent

private const val DESCRIPTOR_LINK_PREFIX = "descriptor:"

/**
 * A descriptor that the reason popup can open.
 */
internal data class DescriptorLink(
  val descriptorName: String,
  val moduleName: String,
)

/**
 * The HTML of the reason popup.
 *
 * @property links the descriptors that the [html] refers to. A link target is the index in this list.
 * @property icons the icons that the [html] refers to, by the `src` attribute of the `icon` element.
 */
internal data class DescriptorSplitModeReasonHtml(
  val html: @Nls String,
  val links: List<DescriptorLink>,
  val icons: Map<String, Icon>,
)

/**
 * The reason why a descriptor has its split-mode kind.
 * It holds names only and no PSI, because the inlay entry holds it.
 *
 * @property presentation the text of the inlay, for example "Backend plugin".
 * @property compatibilityTarget the IDE that the descriptor can be installed into.
 * @property analyzedDescriptor the descriptor of the inlay. A dependency path that does not name its start starts here.
 * @property currentDescriptor the descriptor in the editor, or `null` when it has no module. The popup shows it without a link.
 * It is not [analyzedDescriptor] when the inlay is on a `module` entry of another descriptor.
 * @property analyzedPluginId the plugin id of [analyzedDescriptor], or its plugin name.
 */
internal data class DescriptorSplitModeReason(
  val presentation: @Nls String,
  val compatibilityTarget: @Nls String,
  val analyzedDescriptor: DescriptorLink,
  val currentDescriptor: DescriptorLink?,
  val analyzedPluginId: String?,
  val reason: ModuleKindReason,
) {
  /**
   * Builds the HTML of the reason popup. A dependency path shows as a vertical chain of descriptors.
   *
   * @param descriptorIcons the file icons of the descriptors. A descriptor without an icon here gets a generic icon.
   */
  fun toHtml(descriptorIcons: Map<DescriptorLink, Icon> = emptyMap()): DescriptorSplitModeReasonHtml {
    return ReasonHtmlBuilder(analyzedDescriptor, currentDescriptor, analyzedPluginId, descriptorIcons)
      .build(presentation, compatibilityTarget, reason)
  }
}

/**
 * A descriptor file of the reason popup and the icon that the project view shows for it.
 */
internal data class ResolvedDescriptor(
  val file: VirtualFile,
  val icon: Icon,
)

/**
 * Opens the reason popup at once, with generic descriptor icons.
 * A background read action in smart mode then finds the descriptor files, and the popup shows their file icons.
 * The read action ends when the popup closes.
 */
internal fun showDescriptorSplitModeReasonPopup(
  editor: Editor,
  event: MouseEvent?,
  reason: DescriptorSplitModeReason,
) {
  val project = editor.project ?: return
  var reasonHtml = reason.toHtml()
  var resolvedDescriptors = emptyMap<DescriptorLink, ResolvedDescriptor>()
  val pane = createReasonPane { key -> reasonHtml.icons[key] }
  pane.text = reasonHtml.html
  pane.caretPosition = 0
  lateinit var popup: JBPopup
  pane.addHyperlinkListener(object : HyperlinkAdapter() {
    override fun hyperlinkActivated(e: HyperlinkEvent) {
      val link = e.description?.removePrefix(DESCRIPTOR_LINK_PREFIX)?.toIntOrNull()?.let(reasonHtml.links::getOrNull) ?: return
      popup.cancel()
      val file = resolvedDescriptors[link]?.file
      if (file != null) {
        OpenFileDescriptor(project, file).navigate(true)
      }
      else {
        openDescriptor(project, link)
      }
    }
  })
  popup = JBPopupFactory.getInstance().createComponentPopupBuilder(pane, pane)
    .setRequestFocus(true)
    .setResizable(true)
    .setMovable(true)
    .setCancelOnClickOutside(true)
    .createPopup()
  if (event != null) {
    popup.show(RelativePoint(event))
  }
  else {
    popup.showInBestPositionFor(editor)
  }

  // The current file has no link, but it needs its file icon too.
  val descriptors = (reasonHtml.links + listOfNotNull(reason.analyzedDescriptor, reason.currentDescriptor)).distinct()
  ReadAction.nonBlocking<Map<DescriptorLink, ResolvedDescriptor>> { resolveDescriptors(project, descriptors) }
    .inSmartMode(project)
    .expireWhen { popup.isDisposed }
    .expireWith(project)
    .finishOnUiThread(ModalityState.stateForComponent(pane)) { resolved ->
      resolvedDescriptors = resolved
      reasonHtml = reason.toHtml(resolved.mapValues { it.value.icon })
      pane.text = reasonHtml.html
      pane.caretPosition = 0
    }
    .submit(AppExecutorUtil.getAppExecutorService())
}

/**
 * Finds the file of each descriptor in [links] and computes its file icon.
 */
@RequiresReadLock
@RequiresBackgroundThread
internal fun resolveDescriptors(project: Project, links: Collection<DescriptorLink>): Map<DescriptorLink, ResolvedDescriptor> {
  val psiManager = PsiManager.getInstance(project)
  return links.mapNotNull { link ->
    val file = findDescriptorFile(project, link) ?: return@mapNotNull null
    var icon = IconUtil.computeFileIcon(file, 0, project)
    if (icon is DeferredIcon) {
      // The async evaluator of a DevKit descriptor icon is not available here, so the analysis gives the icon directly.
      val psiFile = psiManager.findFile(file)
      icon = if (psiFile == null) icon.baseIcon else SplitModeModuleKindIcons.computeAccurateDescriptorIcon(psiFile, icon.baseIcon)
    }
    link to ResolvedDescriptor(file, icon)
  }.toMap()
}

/**
 * @param icons gives the icon for the `src` attribute of an `icon` element. The pane calls it each time it gets a new text.
 */
private fun createReasonPane(icons: (String) -> Icon?): JEditorPane {
  val pane = SwingHelper.createHtmlViewer(false, null, null, null)
  pane.editorKit = HTMLEditorKitBuilder()
    .withFontResolver(EditorCssFontResolver.getGlobalInstance())
    .withViewFactoryExtensions(ExtendableHTMLViewFactory.Extensions.icons(icons))
    .build()
  pane.isFocusable = true
  pane.border = JBUI.Borders.empty(8, 10)
  return pane
}

private fun openDescriptor(project: Project, link: DescriptorLink) {
  ReadAction.nonBlocking<VirtualFile?> { findDescriptorFile(project, link) }
    .inSmartMode(project)
    .expireWith(project)
    .finishOnUiThread(ModalityState.nonModal()) { file ->
      if (file != null) {
        OpenFileDescriptor(project, file).navigate(true)
      }
    }
    .submit(AppExecutorUtil.getAppExecutorService())
}

private fun findDescriptorFile(project: Project, link: DescriptorLink): VirtualFile? {
  val module = ModuleManager.getInstance(project).findModuleByName(link.moduleName) ?: return null
  return FilenameIndex.getVirtualFilesByName(link.descriptorName, GlobalSearchScope.moduleScope(module)).minByOrNull { it.path }
}

private class ReasonHtmlBuilder(
  private val analyzedDescriptor: DescriptorLink,
  private val currentDescriptor: DescriptorLink?,
  private val analyzedPluginId: String?,
  private val descriptorIcons: Map<DescriptorLink, Icon>,
) {
  private val links = mutableListOf<DescriptorLink>()
  private val icons = LinkedHashMap<String, Icon>()
  private val secondaryColor = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground())

  fun build(presentation: @Nls String, compatibilityTarget: @Nls String, reason: ModuleKindReason): DescriptorSplitModeReasonHtml {
    val body = HtmlBuilder()
      .append(HtmlChunk.text(presentation).bold())
      .append(HtmlChunk.div().child(secondary(DevKitBundle.message("inlay.split.mode.reason.popup.compatibility", compatibilityTarget))))
    for (section in buildSections(reason)) {
      body.append(HtmlChunk.div("margin-top: 8px").child(section))
    }
    val html = HtmlChunk.html().children(
      HtmlChunk.head().child(HtmlChunk.styleTag("code { font-family: ${EditorCssFontResolver.EDITOR_FONT_NAME_NO_LIGATURES_PLACEHOLDER}; }")),
      HtmlChunk.body().child(body.toFragment()),
    )
    return DescriptorSplitModeReasonHtml(html.toString(), links.toList(), icons.toMap())
  }

  private fun buildSections(reason: ModuleKindReason): List<HtmlChunk> {
    return when (reason) {
      ModuleKindReason.None -> listOf(text(DevKitBundle.message("inlay.split.mode.reason.popup.no.details")))
      is ModuleKindReason.Predefined -> listOf(
        text(DevKitBundle.message("inlay.split.mode.reason.popup.predefined")),
        secondary(reason.reasoning),
      )
      is ModuleKindReason.OwnDependency -> buildList {
        reason.evidence?.let { add(evidenceChain(it)) }
        if (reason.isRpcMissing) {
          add(HtmlBuilder()
                .appendRaw(DevKitBundle.message("inlay.split.mode.reason.popup.no.rpc", code(RPC_MODULE_NAME)))
                .toFragment())
        }
        if (isEmpty()) {
          add(text(DevKitBundle.message("inlay.split.mode.reason.popup.no.details")))
        }
      }
      is ModuleKindReason.MixedDependencies -> buildList {
        add(text(DevKitBundle.message("inlay.split.mode.reason.popup.mixed")))
        reason.frontendEvidence?.let { add(evidenceChain(it)) }
        reason.backendEvidence?.let { add(evidenceChain(it)) }
      }
      is ModuleKindReason.ContainingPlugins -> {
        if (reason.plugins.isEmpty()) {
          listOf(text(DevKitBundle.message("inlay.split.mode.reason.popup.no.containing.plugins")))
        }
        else {
          listOf(
            text(DevKitBundle.message("inlay.split.mode.reason.popup.containing.plugins")),
            chain(reason.plugins.map { plugin ->
              ChainRow.Node(descriptorIcon(plugin.descriptorName, plugin.moduleName),
                            HtmlBuilder()
                              .append(descriptorNode(plugin.descriptorName, plugin.moduleName, plugin.pluginId))
                              .append(" → ")
                              .append(kindIcon(plugin.kind))
                              .append(" ")
                              .append(HtmlChunk.text(getPresentableModuleKind(plugin.kind)).bold())
                              .toFragment())
            }),
          )
        }
      }
      is ModuleKindReason.NoPlatformDependencies -> buildList {
        add(text(DevKitBundle.message("inlay.split.mode.reason.popup.no.platform.dependencies")))
        if (reason.dependencyNames.isNotEmpty()) {
          add(text(DevKitBundle.message("inlay.split.mode.reason.popup.checked.dependencies")))
          add(chain(reason.dependencyNames.map { ChainRow.Node(moduleIcon(), code(it)) }))
        }
      }
    }
  }

  private fun evidenceChain(evidence: KindEvidence): HtmlChunk {
    val title = HtmlBuilder()
      .appendRaw(DevKitBundle.message(
        "inlay.split.mode.reason.popup.evidence",
        HtmlChunk.text(getPresentableModuleKind(evidence.kind)).bold().toString(),
      ))
      .toFragment()
    return HtmlBuilder().append(title).append(chain(buildChainRows(evidence))).toFragment()
  }

  private fun buildChainRows(evidence: KindEvidence): List<ChainRow> {
    val steps = evidence.dependency.trace.steps
    val rows = mutableListOf<ChainRow>()
    if (steps.firstOrNull() !is DependencyTraceStep.Descriptor) {
      rows.add(ChainRow.Node(descriptorIcon(analyzedDescriptor.descriptorName, analyzedDescriptor.moduleName),
                             descriptorNode(analyzedDescriptor.descriptorName, analyzedDescriptor.moduleName, analyzedPluginId)))
    }
    for ((index, step) in steps.withIndex()) {
      when (step) {
        is DependencyTraceStep.Descriptor -> rows.add(ChainRow.Node(descriptorIcon(step.descriptorName, step.moduleName),
                                                                    descriptorNode(step.descriptorName, step.moduleName, step.pluginId)))
        is DependencyTraceStep.ViaDependency -> {
          // The next node shows the name already when it is the dependency itself.
          // An alias or a plugin id differs from the name of the next node, so the edge keeps it.
          val isNamedByNextNode = when (val nextStep = steps.getOrNull(index + 1)) {
            is DependencyTraceStep.Descriptor -> nextStep.descriptorName == "${step.dependencyName}.xml" || nextStep.pluginId == step.dependencyName
            is DependencyTraceStep.Predefined -> nextStep.moduleName == step.dependencyName
            is DependencyTraceStep.ViaDependency,
            is DependencyTraceStep.ContentModule,
            DependencyTraceStep.ContainingPlugin,
            is DependencyTraceStep.ImpliedRpc,
            null,
              -> false
          }
          val dependencyName = if (isNamedByNextNode) HtmlChunk.empty() else code(step.dependencyName)
          rows.add(ChainRow.Edge(edgeLabel("inlay.split.mode.reason.popup.edge.depends.on", dependencyName)))
        }
        is DependencyTraceStep.ContentModule -> rows.add(ChainRow.Edge(edgeLabel("inlay.split.mode.reason.popup.edge.content.module",
                                                                                 text(step.loadingRule.value))))
        DependencyTraceStep.ContainingPlugin -> rows.add(ChainRow.Edge(edgeLabel("inlay.split.mode.reason.popup.edge.containing.plugin")))
        // The kind icon shows the kind that the configuration predefines, so the node does not use the file icon.
        is DependencyTraceStep.Predefined -> rows.add(ChainRow.Node(kindIcon(evidence.kind), HtmlBuilder()
          .append(descriptorNode(step.descriptorName, step.moduleName, step.pluginId))
          .append(" ")
          .append(secondary(DevKitBundle.message("inlay.split.mode.reason.popup.predefined.dependency")))
          .toFragment()))
        is DependencyTraceStep.ImpliedRpc -> rows.add(ChainRow.Edge(edgeLabel(
          "inlay.split.mode.reason.popup.edge.implied.rpc",
          code(step.dependencyName),
          text(step.kind.id),
        )))
      }
    }
    when (steps.lastOrNull()) {
      is DependencyTraceStep.Descriptor, null -> {
        rows.add(ChainRow.Edge(edgeLabel("inlay.split.mode.reason.popup.edge.depends.on", HtmlChunk.empty())))
        rows.add(ChainRow.Node(kindIcon(evidence.kind), code(evidence.dependency.name)))
      }
      is DependencyTraceStep.ImpliedRpc -> rows.add(ChainRow.Node(moduleIcon(), code(evidence.dependency.name)))
      is DependencyTraceStep.Predefined,
      is DependencyTraceStep.ViaDependency,
      is DependencyTraceStep.ContentModule,
      DependencyTraceStep.ContainingPlugin,
        -> {}
    }
    return rows
  }

  private fun chain(rows: List<ChainRow>): HtmlChunk {
    val table = HtmlChunk.tag("table").attr("cellspacing", 0).attr("cellpadding", 1)
    return table.children(rows.map { row ->
      val markerCell = HtmlChunk.tag("td").attr("valign", "top").attr("align", "left").attr("width", 20)
      when (row) {
        is ChainRow.Node -> HtmlChunk.tag("tr").children(markerCell.child(row.icon), HtmlChunk.tag("td").child(row.content))
        // The arrow starts under the name of the node above it.
        // Swing HTML loses the font of a `code` element in a `span` element, so the cell gives the color of an edge.
        is ChainRow.Edge -> HtmlChunk.tag("tr").children(
          markerCell,
          HtmlChunk.tag("td").style("color: $secondaryColor").children(HtmlChunk.text("↓ "), row.content),
        )
      }
    })
  }

  /**
   * A content module descriptor, named `<module>.xml`, shows as its module, because a dependency names the module.
   * Another descriptor, for example `plugin.xml`, shows its file name and its [pluginId].
   * The popup does not show the IntelliJ module of such a descriptor, because the plugin model does not use it.
   * The current descriptor has no link.
   */
  private fun descriptorNode(descriptorName: String, moduleName: String?, pluginId: String? = null): HtmlChunk {
    if (moduleName == null) {
      return HtmlChunk.text(descriptorName)
    }
    val isModuleDescriptor = descriptorName == "$moduleName.xml"
    val name = if (isModuleDescriptor) moduleName else descriptorName
    val isCurrent = DescriptorLink(descriptorName, moduleName) == currentDescriptor
    val builder = HtmlBuilder()
    if (isCurrent) {
      builder.append(name)
    }
    else {
      links.add(DescriptorLink(descriptorName, moduleName))
      builder.append(HtmlChunk.link("$DESCRIPTOR_LINK_PREFIX${links.lastIndex}", name))
    }
    if (!isModuleDescriptor && pluginId != null) {
      builder.append(" ").append(secondary(DevKitBundle.message("inlay.split.mode.reason.popup.plugin", pluginId)))
    }
    if (isCurrent) {
      builder.append(" ").append(secondary(DevKitBundle.message("inlay.split.mode.reason.popup.current")))
    }
    return builder.toFragment()
  }

  private fun descriptorIcon(descriptorName: String, moduleName: String?): HtmlChunk {
    val fileIcon = moduleName?.let { descriptorIcons[DescriptorLink(descriptorName, it)] }
    return when {
      fileIcon != null -> icon("file:$moduleName/$descriptorName", fileIcon)
      descriptorName == PluginManagerCore.PLUGIN_XML -> icon("plugin", AllIcons.Nodes.Plugin)
      else -> icon("descriptor", DevkitCoreIcons.PluginV2)
    }
  }

  private fun kindIcon(kind: ModuleKind): HtmlChunk {
    return when (kind) {
      ModuleKind.FRONTEND,
      ModuleKind.LITE_FRONTEND,
        -> icon("frontend", DevkitCoreIcons.FrontendModule)
      ModuleKind.BACKEND -> icon("backend", DevkitCoreIcons.BackendModule)
      ModuleKind.SHARED -> icon("shared", DevkitCoreIcons.SharedModule)
      ModuleKind.MONOLITH,
      ModuleKind.MIXED,
      is ModuleKind.Composite,
        -> moduleIcon()
    }
  }

  private fun moduleIcon(): HtmlChunk = icon("module", AllIcons.Nodes.Module)

  private fun icon(key: String, icon: Icon): HtmlChunk {
    icons[key] = icon
    return HtmlChunk.icon(key, icon)
  }

  private fun edgeLabel(messageKey: @PropertyKey(resourceBundle = DevKitBundle.BUNDLE) String, vararg arguments: HtmlChunk): HtmlChunk {
    return HtmlChunk.raw(DevKitBundle.message(messageKey, *arguments.map { it.toString() }.toTypedArray()))
  }

  private fun secondary(text: @Nls String): HtmlChunk = HtmlChunk.span("color: $secondaryColor").addText(text)

  private fun text(text: @Nls String): HtmlChunk = HtmlChunk.text(text)

  private fun code(text: @Nls String): HtmlChunk = HtmlChunk.text(text).code()
}

private sealed interface ChainRow {
  val content: HtmlChunk

  /** A descriptor or a dependency. The [icon] shows the type of the node. */
  class Node(val icon: HtmlChunk, override val content: HtmlChunk) : ChainRow

  /** The relation between two nodes. */
  class Edge(override val content: HtmlChunk) : ChainRow
}
