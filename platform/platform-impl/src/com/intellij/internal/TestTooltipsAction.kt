// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.ide.HelpTooltip
import com.intellij.ide.IdeTooltip
import com.intellij.ide.IdeTooltipManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.impl.EditorCssFontResolver
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.ColorUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ReflectionUtil
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.Point
import java.awt.Toolkit
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URL
import javax.swing.JEditorPane
import javax.swing.JTree
import javax.swing.tree.DefaultTreeCellRenderer

class TestTooltipsAction: DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val panel = panel {
      row("javax.swing.JComponent.setToolTipText") { label("Standard AWT tooltip").applyToComponent { toolTipText = "Plain tooltip" } }
      row("javax.swing.JComponent.setToolTipText") {
        label("Standard AWT tooltip with HTML").applyToComponent { toolTipText = "<html>Tooltip with <b>HTML</b><br>features" }
      }
      row("com.intellij.ide.HelpTooltip") {
        label("HelpTooltip with web link").applyToComponent {
          HelpTooltip().setTitle("Title").setDescription("<html>Some <i>description</i>")
            .setBrowserLink("clickable web link", URL("https://www.jetbrains.com/"))
            .installOn (this) }
      }
      row("com.intellij.ide.HelpTooltip") {
        label("HelpTooltip with beep link").applyToComponent {
          HelpTooltip().setTitle("Title").setDescription("<html>Some <i>description</i>")
            .setLink("clickable BEEP link", Runnable { Toolkit.getDefaultToolkit().beep() })
            .installOn (this) }
      }
      row("com.intellij.ide.IdeTooltip") {
        label("Custom IDE tooltip with JTree").applyToComponent {
          val tooltip = IdeTooltip(this, Point(40, 40), JTree().apply {
            isOpaque = false
            (cellRenderer as DefaultTreeCellRenderer).apply {
              ReflectionUtil.setField(DefaultTreeCellRenderer::class.java, this, java.lang.Boolean.TYPE, "fillBackground", false)
            }
          }, System.identityHashCode(this)).apply {
            isHint = true
            preferredPosition = Balloon.Position.above
            textBackground = ColorUtil.mix(JBColor.background(), JBColor.PINK, .5)
          }
          IdeTooltipManager.getInstance().setCustomTooltip(this, tooltip)
        }
      }
      row("com.intellij.ui.popup.AbstractPopup") {
        label("Popup with a component inside").applyToComponent {
          addMouseListener(object:MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
              val popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(ScrollPaneFactory.createScrollPane(createEditorPane()), null)
                .createPopup()
              popup.size = Dimension(450, 300)
              popup.showInCenterOf(this@applyToComponent)
            }
          })
        }
      }
    }
    dialog("Test Tooltips", panel, createActions = { emptyList() } ).show()
  }
  private fun createEditorPane() : JEditorPane {
    val pane = JEditorPane()
    pane.border = JBUI.Borders.empty(10)
    val editorKit = HTMLEditorKitBuilder().build()
    UIUtil.doNotScrollToCaret(pane)
    pane.editorKit = editorKit
    pane.text = "<html><head><base href=\"jar:file:////Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/src.zip!/java/io/File.java\"></head><body><div class='definition'><pre><span style=\"color:#0033b3;\">public</span> <span style=\"color:#0033b3;\">class</span> <span style=\"color:#000000;\">File</span> <span style=\"color:#0033b3;\">implements</span> <a href=\"psi_element://java.io.Serializable\"><code style='font-size:100%;'><span style=\"color:#000000;\">java.io.Serializable</span></code></a><span style=\"\">,</span>&nbsp;<a href=\"psi_element://java.lang.Comparable\"><code style='font-size:100%;'><span style=\"color:#000000;\">Comparable</span></code></a><span style=\"\">&lt;</span><a href=\"psi_element://java.io.File\"><code style='font-size:100%;'><span style=\"color:#000000;\">File</span></code></a><span style=\"\">&gt;</span></pre></div><div class='content'>   An abstract representation of file and directory pathnames.     <p> User interfaces and operating systems use system-dependent <em>pathname   strings</em> to name files and directories.  This class presents an   abstract, system-independent view of hierarchical pathnames.  An   <em>abstract pathname</em> has two components:     <ol>   <li> An optional system-dependent <em>prefix</em> string,        such as a disk-drive specifier, <code>\"/\"</code>&nbsp;for the UNIX root        directory, or <code>\"\\\\\\\\\"</code>&nbsp;for a Microsoft Windows UNC pathname, and   <li> A sequence of zero or more string <em>names</em>.   </ol>     The first name in an abstract pathname may be a directory name or, in the   case of Microsoft Windows UNC pathnames, a hostname.  Each subsequent name   in an abstract pathname denotes a directory; the last name may denote   either a directory or a file.  The <em>empty</em> abstract pathname has no   prefix and an empty name sequence.     <p> The conversion of a pathname string to or from an abstract pathname is   inherently system-dependent.  When an abstract pathname is converted into a   pathname string, each name is separated from the next by a single copy of   the default <em>separator character</em>.  The default name-separator   character is defined by the system property <code>file.separator</code>, and   is made available in the public static fields <code><a href=\"psi_element://java.io.File#separator\"><code style='font-size:100%;'>separator</code></a></code> and <code><a href=\"psi_element://java.io.File#separatorChar\"><code style='font-size:100%;'>separatorChar</code></a></code> of this class.   When a pathname string is converted into an abstract pathname, the names   within it may be separated by the default name-separator character or by any   other name-separator character that is supported by the underlying system.     <p> A pathname, whether abstract or in string form, may be either   <em>absolute</em> or <em>relative</em>.  An absolute pathname is complete in   that no other information is required in order to locate the file that it   denotes.  A relative pathname, in contrast, must be interpreted in terms of   information taken from some other pathname.  By default the classes in the   <code>java.io</code> package always resolve relative pathnames against the   current user directory.  This directory is named by the system property   <code>user.dir</code>, and is typically the directory in which the Java   virtual machine was invoked.     <p> The <em>parent</em> of an abstract pathname may be obtained by invoking   the <a href=\"psi_element://java.io.File#getParent()\"><code style='font-size:100%;'>getParent</code></a> method of this class and consists of the pathname's   prefix and each name in the pathname's name sequence except for the last.   Each directory's absolute pathname is an ancestor of any <tt>File</tt>   object with an absolute abstract pathname which begins with the directory's   absolute pathname.  For example, the directory denoted by the abstract   pathname <tt>\"/usr\"</tt> is an ancestor of the directory denoted by the   pathname <tt>\"/usr/local/bin\"</tt>.     <p> The prefix concept is used to handle root directories on UNIX platforms,   and drive specifiers, root directories and UNC pathnames on Microsoft Windows platforms,   as follows:     <ul>     <li> For UNIX platforms, the prefix of an absolute pathname is always   <code>\"/\"</code>.  Relative pathnames have no prefix.  The abstract pathname   denoting the root directory has the prefix <code>\"/\"</code> and an empty   name sequence.     <li> For Microsoft Windows platforms, the prefix of a pathname that contains a drive   specifier consists of the drive letter followed by <code>\":\"</code> and   possibly followed by <code>\"\\\\\"</code> if the pathname is absolute.  The   prefix of a UNC pathname is <code>\"\\\\\\\\\"</code>; the hostname and the share   name are the first two names in the name sequence.  A relative pathname that   does not specify a drive has no prefix.     </ul>     <p> Instances of this class may or may not denote an actual file-system   object such as a file or a directory.  If it does denote such an object   then that object resides in a <i>partition</i>.  A partition is an   operating system-specific portion of storage for a file system.  A single   storage device (e.g. a physical disk-drive, flash memory, CD-ROM) may   contain multiple partitions.  The object, if any, will reside on the   partition <a name=\"partName\">named</a> by some ancestor of the absolute   form of this pathname.     <p> A file system may implement restrictions to certain operations on the   actual file-system object, such as reading, writing, and executing.  These   restrictions are collectively known as <i>access permissions</i>.  The file   system may have multiple sets of access permissions on a single object.   For example, one set may apply to the object's <i>owner</i>, and another   may apply to all other users.  The access permissions on an object may   cause some methods in this class to fail.     <p> Instances of the <code>File</code> class are immutable; that is, once   created, the abstract pathname represented by a <code>File</code> object   will never change.     <h3>Interoperability with <code style='font-size:100%;'>java.nio.file</code> package</h3>     <p> The <a href=\"psi_element://java.nio.file\"><code style='font-size:100%;'>java.nio.file</code></a>   package defines interfaces and classes for the Java virtual machine to access   files, file attributes, and file systems. This API may be used to overcome   many of the limitations of the <code style='font-size:100%;'>java.io.File</code> class.   The <a href=\"psi_element://java.io.File#toPath()\"><code style='font-size:100%;'>toPath</code></a> method may be used to obtain a <a href=\"psi_element://java.nio.file.Path\"><code style='font-size:100%;'>Path</code></a> that uses the abstract path represented by a <code style='font-size:100%;'>File</code> object to   locate a file. The resulting <code style='font-size:100%;'>Path</code> may be used with the <a href=\"psi_element://java.nio.file.Files\"><code style='font-size:100%;'>java.nio.file.Files</code></a> class to provide more efficient and extensive access to   additional file operations, file attributes, and I/O exceptions to help   diagnose errors when an operation on a file fails.     </div><table class='sections'><p><tr><td valign='top' class='section'><p>Since:</td><td valign='top'><p>   JDK1.0</td></table><div class=\"bottom\"><icon src=\"AllIcons.Nodes.Package\">&nbsp;<a href=\"psi_element://java.io\"><code style='font-size:100%;'>java.io</code></a></div><div class=\"bottom\"><icon src=\"0\"/>&nbsp;&lt; 1.8_112 &gt; (rt.jar)</div>"

    val cssRules = getDocumentationPaneDefaultCssRules()
    for (rule in cssRules) {
      editorKit.styleSheet.addRule(rule)
    }
    return pane
  }
  fun getDocumentationPaneDefaultCssRules(): List<String> {
    val leftPadding = 8
    val definitionTopPadding = 4
    val linkColor = ColorUtil.toHtmlColor(JBUI.CurrentTheme.Link.Foreground.ENABLED)
    val borderColor = ColorUtil.toHtmlColor(UIUtil.getTooltipSeparatorColor())
    val sectionColor = ColorUtil.toHtmlColor(Gray.get(0x90))
    val editorFontStyle = "{ font-family:\"" + EditorCssFontResolver.EDITOR_FONT_NAME_NO_LIGATURES_PLACEHOLDER + "\";" +
                          "font-size:" + getMonospaceFontSizeCorrection() + "%; }"
    return java.util.List.of(
      "tt $editorFontStyle",
      "code $editorFontStyle",
      "pre $editorFontStyle",
      ".pre $editorFontStyle",
      "html { padding-bottom: 8px; }",
      "h1, h2, h3, h4, h5, h6 { margin-top: 0; padding-top: 1px; }",
      "a { color: $linkColor; text-decoration: none;}",
      ".definition { padding: " + definitionTopPadding + "px 17px 1px " + leftPadding + "px;" +
      "              border-bottom: thin solid " + borderColor + "; }",
      ".definition-only { padding: " + definitionTopPadding + "px 17px 0 " + leftPadding + "px; }",
      ".definition-only pre { margin-bottom: 0 }",
      ".content { padding: 5px 16px 0 " + leftPadding + "px; max-width: 100% }",
      (".content-separated { padding: 5px 16px 5px " + leftPadding + "px; max-width: 100%;" +
       "                     border-bottom: thin solid " + borderColor + "; }"),
      ".content-only { padding: 8px 16px 0 " + leftPadding + "px; max-width: 100% }",
      ".bottom { padding: 3px 16px 0 " + leftPadding + "px; }",
      ".bottom-no-content { padding: 5px 16px 0 " + leftPadding + "px; }",
      "p { padding: 1px 0 2px 0; }",
      "ol { padding: 0 16px 0 0; }",
      "ul { padding: 0 16px 0 0; }",
      "li { padding: 1px 0 2px 0; }",
      ".grayed { color: #909090; display: inline;}",
      ".centered { text-align: center}",  // sections table
      ".sections { padding: 0 16px 0 " + leftPadding + "px; border-spacing: 0; }",
      "tr { margin: 0 0 0 0; padding: 0 0 0 0; }",
      "table p { padding-bottom: 0}",
      "td { margin: 4px 0 0 0; padding: 0 0 0 0; }",
      "th { text-align: left; }",
      "td pre { padding: 1px 0 0 0; margin: 0 0 0 0 }",
      ".section { color: $sectionColor; padding-right: 4px; white-space:nowrap;}"
    )
  }
  private fun getMonospaceFontSizeCorrection(): Int {
    return if (SystemInfo.isWin10OrNewer && !ApplicationManager.getApplication().isUnitTestMode) 96 else 100
  }
}