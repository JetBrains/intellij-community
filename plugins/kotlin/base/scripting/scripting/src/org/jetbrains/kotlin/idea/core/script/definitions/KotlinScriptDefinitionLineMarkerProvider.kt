// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.definitions

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.core.script.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.settings.DISCOVERY_DOCUMENTATION_URL
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettingsConfigurable
import org.jetbrains.kotlin.psi.KtClass
import java.awt.BorderLayout
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Marks a `@KotlinScript` class in the gutter.
 *
 * The icon fades while no marker file declares the class, so the user sees the state without a click.
 * A script file carries no marker here, because [KotlinScriptDefinitionCodeVisionProvider] already
 * names its definition above the first line.
 */
class KotlinScriptDefinitionLineMarkerProvider : LineMarkerProviderDescriptor() {

    override fun getName(): String = KotlinBaseScriptingBundle.message("gutter.script.definition.name")

    override fun getIcon(): Icon = KotlinIcons.SCRIPT

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val klass = element.parent as? KtClass ?: return null
        if (klass.nameIdentifier !== element) return null
        if (!hasKotlinScriptAnnotation(klass)) return null

        val fqName = klass.fqName?.asString() ?: return null
        val module = ModuleUtilCore.findModuleForPsiElement(klass)
        val markerFile = module?.let { findScriptTemplateMarkerFile(it, fqName) }
        val markerStep = MarkerStep.of(markerFile)
        // A marker file anywhere on the classpath makes the definition discoverable.
        val discovered = markerFile != null || isScriptTemplateInDependency(klass.project, fqName)

        return LineMarkerInfo(
            element,
            element.textRange,
            if (discovered) KotlinIcons.SCRIPT else FADED_SCRIPT_ICON,
            { markerStep.tooltip },
            { event, target -> showDefinitionPopup(event, target.project, module, fqName, markerFile) },
            GutterIconRenderer.Alignment.LEFT,
            // A screen reader gets the sentence without the markup around it.
            { markerStep.title },
        )
    }

    /**
     * Shows what automatic discovery still needs.
     *
     * A marker file alone does not make a definition usable, so the popup lists both prerequisites
     * together and never reports the definition as ready.
     *
     * The page this popup leads to is a projectConfigurable of the same backend module, so the popup
     * shares its split-mode limit. Both move together when scripting settings reach the frontend.
     */
    @Suppress("SplitModeApiUsage")
    private fun showDefinitionPopup(
        event: MouseEvent,
        project: Project,
        module: Module?,
        fqName: String,
        markerFile: VirtualFile?,
    ) {
        val markerPath = scriptTemplateMarkerPath(fqName)
        val markerStep = MarkerStep.of(markerFile)
        var popup: JBPopup? = null
        // Every link closes the popup first, so the action never runs behind it.
        fun onClick(perform: () -> Unit): () -> Unit = {
            popup?.cancel()
            perform()
        }

        val content = JPanel(VerticalLayout(JBUI.scale(6))).apply {
            border = JBUI.Borders.empty(8, 12)
            add(JBLabel(KotlinBaseScriptingBundle.message("gutter.script.definition.setup.title")).apply { font = JBFont.small() })

            add(
                if (markerFile == null) {
                    step(markerStep.icon, markerStep.title)
                }
                else {
                    step(
                        markerStep.icon,
                        markerStep.title,
                        secondary(markerPath),
                    )
                }
            )
            add(
                step(
                    AllIcons.General.Information,
                    KotlinBaseScriptingBundle.message("gutter.script.definition.setup.jar"),
                    secondary(KotlinBaseScriptingBundle.message("gutter.script.definition.setup.jar.hint")),
                )
            )

            add(SeparatorComponent(JBUI.scale(2), UIUtil.getContextHelpForeground(), null))

            if (markerFile != null) {
                // A marker file carries no file type, so the editor cannot show it. The project view can.
                add(link("gutter.script.definition.action.open.marker", onClick { revealScriptTemplateMarker(project, markerFile) }))
            }
            else if (module != null) {
                add(
                    link("gutter.script.definition.action.create.marker", onClick {
                        createAndReportScriptTemplateMarkerFile(project, module, fqName)
                    })
                )
            }
            add(link("gutter.script.definition.action.settings", onClick { showSettings(project) }))
            add(
                link(
                    "gutter.script.definition.action.documentation",
                    onClick { BrowserUtil.browse(DISCOVERY_DOCUMENTATION_URL) },
                    external = true,
                )
            )
        }

        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(content, null)
            .setTitle(KotlinBaseScriptingBundle.message("gutter.script.definition.popup.title"))
            .setRequestFocus(true)
            .setResizable(false)
            .setMovable(false)
            .createPopup()
        popup.show(RelativePoint(event))
    }

    /** One prerequisite: an icon, what it is, and anything that explains it. */
    private fun step(icon: Icon, @NlsContexts.Label title: String, vararg details: JComponent): JComponent =
        JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            isOpaque = false
            add(JBLabel(icon).apply { verticalAlignment = SwingConstants.TOP }, BorderLayout.LINE_START)
            add(
                JPanel(VerticalLayout(JBUI.scale(2))).apply {
                    isOpaque = false
                    add(JBLabel(title).apply { font = JBFont.small() })
                    details.forEach { add(it) }
                },
                BorderLayout.CENTER,
            )
        }

    private fun secondary(@NlsContexts.Label text: String): JComponent =
        JBLabel(text).apply {
            font = JBFont.small()
            foreground = UIUtil.getContextHelpForeground()
        }

    private fun link(key: String, perform: () -> Unit, external: Boolean = false): JComponent =
        ActionLink(KotlinBaseScriptingBundle.message(key)) { perform() }.apply {
            font = JBFont.small()
            if (external) {
                // The platform draws its own external-link icon, so the label never holds an arrow.
                setExternalLinkIcon()
                iconTextGap = JBUI.scale(4)
                accessibleContext.accessibleDescription =
                    KotlinBaseScriptingBundle.message("gutter.script.definition.action.documentation.icon.description")
            }
        }

    private fun showSettings(project: Project) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, KotlinScriptingSettingsConfigurable::class.java)
    }
}

/**
 * A definition that no marker file declares is not discovered, so its icon fades.
 *
 * The alpha is the one a temporary run configuration uses, in `ProgramRunnerUtil.getTemporaryIcon`.
 * Calling that method here would pull in the execution module for one icon.
 */
private val FADED_SCRIPT_ICON: Icon = IconLoader.getTransparentIcon(KotlinIcons.SCRIPT, 0.45f)

/**
 * Whether a marker file declares the definition.
 *
 * The popup draws [icon] beside [title], and the gutter tooltip shows the same pair as HTML. Both
 * read this one place, so a hover and a click never show a different icon or a different sentence.
 * [iconPath] is what the HTML of a tooltip resolves the icon by.
 */
private enum class MarkerStep(val icon: Icon, private val iconPath: String, private val titleKey: String) {
    FOUND(AllIcons.General.InspectionsOK, "AllIcons.General.InspectionsOK", "gutter.script.definition.setup.marker.found"),
    MISSING(AllIcons.General.Warning, "AllIcons.General.Warning", "gutter.script.definition.setup.marker.missing");

    val title: @NlsContexts.Label String
        get() = KotlinBaseScriptingBundle.message(titleKey)

    val tooltip: @NlsSafe String
        get() = HtmlChunk.html().children(
            listOf(HtmlChunk.icon(iconPath, icon), HtmlChunk.nbsp(), HtmlChunk.text(title))
        ).toString()

    companion object {
        fun of(markerFile: VirtualFile?): MarkerStep = if (markerFile == null) MISSING else FOUND
    }
}
