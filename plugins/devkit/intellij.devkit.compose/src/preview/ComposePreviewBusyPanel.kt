// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.preview

import com.intellij.devkit.compose.DevkitComposeBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ClickListener
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Font
import java.awt.Point
import java.awt.event.MouseEvent
import java.util.Collections
import java.util.IdentityHashMap
import java.util.regex.Pattern
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.JTextPane
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

internal class ComposePreviewBusyPanel(private val project: Project) : JBPanelWithEmptyText(BorderLayout()), DumbAware {
  @Volatile
  private var busy: Boolean = false
  private var busyIcon: AsyncProcessIcon? = null

  // Stored error info for toggling between collapsed and expanded error views
  @NlsSafe
  private var lastErrorMessage: String? = null
  private var lastStackTrace: String? = null
  private var errorSourceFile: VirtualFile? = null

  // Pattern to match stack trace lines: at com.package.Class.method(File.kt:42)
  private val stackTraceLinePattern = Pattern.compile("^(\\s+)at\\s+(.+?)\\.(.+?)\\((.+?):(\\d+)\\)$")

  init {
    emptyText.isCenterAlignText = false
    displayDefaultContent()
  }

  fun displayDefaultContent() {
    removeAll()
    emptyText.clear()
    emptyText.appendLine(DevkitComposeBundle.message("compose.preview.empty.text.top"))
    emptyText.appendLine(DevkitComposeBundle.message("compose.preview.empty.text.compile"))
    emptyText.appendLine("")
    appendBuildHintText(emptyText, project)
    revalidate()
    repaint()
  }

  private fun appendBuildHintText(text: StatusText, project: Project) {
    val buildLine = DevkitComposeBundle.message("compose.preview.build")
    text.appendLineWithLink(buildLine) {
      val compileAction = ActionManager.getInstance().getAction("CompileDirty")!!
      val dataContext = SimpleDataContext.builder().add(PROJECT, project).build()
      ActionUtil.performAction(compileAction, AnActionEvent.createEvent(dataContext, Presentation(), "", ActionUiKind.NONE, null))
    }
    val shortcut = ActionManager.getInstance().getKeyboardShortcut("CompileDirty")
    val shortcutText = shortcut?.let { " (${KeymapUtil.getShortcutText(shortcut)})" } ?: ""
    text.appendText(shortcutText)

    addRefreshHintText(text, project)
  }

  private fun addRefreshHintText(text: StatusText, project: Project) {
    val refreshLine = DevkitComposeBundle.message("compose.preview.refresh")
    text.appendLineWithLink(refreshLine) {
      project.service<ComposePreviewChangesTracker>().refresh()
    }
  }

  @Suppress("HardCodedStringLiteral")
  private fun StatusText.appendLineWithLink(line: String, action: () -> Unit) {
    appendLine(line.substringBefore('<'))
    appendText(line.substringAfter('<').substringBefore('>'), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) { action() }
    appendText(line.substringAfter(">"))
  }

  fun setPaintBusy(paintBusy: Boolean) {
    if (busy == paintBusy) return

    busy = paintBusy
    updateBusy()

    revalidate()
    repaint()
  }

  private fun updateBusy() {
    if (busy) {
      if (busyIcon == null) {
        busyIcon = AsyncProcessIcon.Big(toString())
        busyIcon!!.setOpaque(false)
        busyIcon!!.setPaintPassiveIcon(false)
        add(busyIcon!!, BorderLayout.CENTER)
      }
    }

    val current = busyIcon
    if (current != null) {
      if (busy) {
        removeAll()
        add(current, BorderLayout.CENTER)
        current.resume()
      }
      else {
        current.suspend()
        SwingUtilities.invokeLater(Runnable {
          if (busyIcon != null) {
            repaint()
          }
        })
      }
      current.updateLocation(this)
    }
  }

  fun displayUnsupportedFile() {
    removeAll()

    emptyText.clear()
    emptyText.appendLine(AllIcons.Ide.FatalErrorRead, DevkitComposeBundle.message("compose.preview.unsupported.file"),
                         StatusText.DEFAULT_ATTRIBUTES, null)
    emptyText.appendLine(DevkitComposeBundle.message("compose.preview.enable.composable"))

    addRefreshHintText(emptyText, project)

    revalidate()
    repaint()
  }

  fun displayMissingLocals(e: ComposeLocalContextException) {
    removeAll()

    emptyText.clear()
    emptyText.appendLine(AllIcons.Ide.FatalErrorRead,
                         e.cause?.message ?: DevkitComposeBundle.message("compose.preview.insufficient.local.context"),
                         StatusText.DEFAULT_ATTRIBUTES,
                         null)
    emptyText.appendLine(DevkitComposeBundle.message("compose.preview.insufficient.local.hint"))

    addRefreshHintText(emptyText, project)

    revalidate()
    repaint()
  }

  fun displayError(e: Throwable, sourceFile: VirtualFile) {
    val rootCause = e.rootCause()
    lastErrorMessage = rootCause.message ?: DevkitComposeBundle.message("compose.preview.render.error.unknown")
    lastStackTrace = e.stackTraceToString()
    errorSourceFile = sourceFile

    showCompactErrorView()
  }

  private fun showCompactErrorView() {
    removeAll()

    emptyText.clear()
    val errorMessage = lastErrorMessage ?: return
    emptyText.appendLine(AllIcons.Ide.FatalErrorRead, DevkitComposeBundle.message("compose.preview.render.error"),
                         StatusText.DEFAULT_ATTRIBUTES, null)
    emptyText.appendLine(errorMessage)

    val stackTrace = lastStackTrace ?: return
    val showDetailsLine = DevkitComposeBundle.message("compose.preview.render.error.show.details")
    emptyText.appendLineWithLink(showDetailsLine) {
      showExpandedErrorView(stackTrace.lines().map(::resolveStackTraceLine))
    }

    appendBuildHintText(emptyText, project)

    revalidate()
    repaint()
  }

  private fun showExpandedErrorView(lines: List<StackTraceLine>) {
    removeAll()
    val detailsPanel = JPanel(BorderLayout())

    val titleBar = JPanel(BorderLayout()).apply {
      border = JBUI.Borders.empty(4, 8)
      background = UIUtil.getPanelBackground()
    }
    val titleLabel = JBLabel(DevkitComposeBundle.message("compose.preview.render.error.title"))
    titleLabel.font = titleLabel.font.deriveFont(Font.BOLD)
    titleBar.add(titleLabel, BorderLayout.WEST)
    val closeBtn = HyperlinkLabel(DevkitComposeBundle.message("compose.preview.render.error.close"))
    closeBtn.addHyperlinkListener { _ -> showCompactErrorView() }
    titleBar.add(closeBtn, BorderLayout.EAST)
    detailsPanel.add(titleBar, BorderLayout.NORTH)

    val scrollPane = JBScrollPane(createStackTraceTextPane(lines)).apply {
      border = JBUI.Borders.empty(4)
      verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
      horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
      preferredSize = JBUI.size(0, 200)
    }
    detailsPanel.add(scrollPane, BorderLayout.CENTER)

    val hintPanel = JBPanelWithEmptyText(BorderLayout())
    hintPanel.emptyText.clear()
    appendBuildHintText(hintPanel.emptyText, project)
    detailsPanel.add(hintPanel, BorderLayout.SOUTH)

    add(detailsPanel, BorderLayout.CENTER)
    revalidate()
    repaint()
  }

  private fun resolveStackTraceLine(text: String): StackTraceLine {
    val matcher = stackTraceLinePattern.matcher(text)
    if (matcher.matches()) {
      val lineNum = matcher.group(5).toInt()
      val virtualFile = errorSourceFile?.takeIf { it.name == matcher.group(4) }
      if (virtualFile != null) {
        return StackTraceLine(text, virtualFile, lineNum)
      }
    }
    return StackTraceLine(text, null, null)
  }

  private fun createStackTraceTextPane(lines: List<StackTraceLine>): JTextPane {
    val links = mutableListOf<StackTraceLink>()
    return JTextPane().apply {
      isEditable = false
      isOpaque = false
      val linkAttributes = SimpleAttributeSet().apply {
        StyleConstants.setForeground(this, JBUI.CurrentTheme.Link.Foreground.ENABLED)
        StyleConstants.setUnderline(this, true)
      }
      lines.forEachIndexed { index, line ->
        val virtualFile = line.virtualFile
        val lineNumber = line.lineNumber
        if (virtualFile == null || lineNumber == null) {
          styledDocument.insertString(styledDocument.length, line.text, null)
        }
        else {
          styledDocument.insertString(styledDocument.length, "${line.text.substringBeforeLast('(')}(", null)
          val start = styledDocument.length
          styledDocument.insertString(styledDocument.length, "${virtualFile.name}:$lineNumber", linkAttributes)
          links.add(StackTraceLink(start, styledDocument.length, virtualFile, lineNumber))
          styledDocument.insertString(styledDocument.length, ")", null)
        }
        if (index != lines.lastIndex) {
          styledDocument.insertString(styledDocument.length, "\n", null)
        }
      }
      object : ClickListener() {
        override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
          val link = linkAt(event.x, event.y, links) ?: return false
          navigateTo(link)
          return true
        }
      }.installOn(this)
    }
  }

  private fun JTextPane.linkAt(x: Int, y: Int, links: List<StackTraceLink>): StackTraceLink? {
    val offset = viewToModel2D(Point(x, y))
    return links.firstOrNull { offset in it.start until it.end }
  }

  private fun navigateTo(link: StackTraceLink) {
    // Avoid instantly removing the error when navigation to another file
    if (link.virtualFile != errorSourceFile) project.service<ComposePreviewChangesTracker>().setAutoRefresh(false)
    OpenFileDescriptor(project, link.virtualFile, link.lineNumber - 1, 0).navigate(true)
  }

  private data class StackTraceLine(val text: String, val virtualFile: VirtualFile?, val lineNumber: Int?)

  private data class StackTraceLink(val start: Int, val end: Int, val virtualFile: VirtualFile, val lineNumber: Int)

  fun setContent(content: JComponent) {
    removeAll()

    add(content, BorderLayout.CENTER)
    revalidate()
    repaint()
  }
}

internal fun Throwable.rootCause(): Throwable {
  val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
  var current = this
  while (visited.add(current)) {
    val cause = current.cause ?: break
    if (visited.contains(cause)) break
    current = cause
  }
  return current
}
