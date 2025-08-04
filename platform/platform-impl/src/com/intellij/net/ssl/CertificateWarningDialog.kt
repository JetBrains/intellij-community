// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.net.ssl

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.*
import com.intellij.ui.CheckboxTree.CheckboxTreeCellRenderer
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.net.ssl.CertificateManager
import com.intellij.util.net.ssl.CertificateProvider
import com.intellij.util.net.ssl.CertificateWrapper
import com.intellij.util.net.ssl.CertificateWrapper.CommonField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.ScreenReader
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Color
import java.awt.Component
import java.awt.Point
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.text.DateFormat
import javax.net.ssl.X509ExtendedTrustManager
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JTextPane
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreeSelectionModel


@Suppress("DialogTitleCapitalization")
internal class CertificateWarningDialog(
  private val certificates: List<X509Certificate>,
  private val remoteHost: @NlsSafe String? = null,
  private val manager: X509ExtendedTrustManager,
  private val authType: String,
  private val certificateProvider: CertificateProvider,
) : DialogWrapper(false) {
  private var expandedByButton = false

  private val certificateErrorsMap = getCertificateErrorsMap()
  private val hasUntrusted = certificateErrorsMap.entries.any { it.value.contains(CertificateError.UNTRUSTED_AUTHORITY) }
  private var currentCertificate = CertificateWrapper(certificates.reversed().firstOrNull { certificateErrorsMap[it]!!.isNotEmpty() }
                                                      ?: certificates.first())

  private val tree = createCertificateTree()
  private val errorColor = JBColor.namedColor("Label.errorForeground", JBColor.red)
  private var isDetailsShown = false
  private lateinit var detailsPlaceholder: Placeholder
  private lateinit var detailsCollapsibleRow: CollapsibleRow

  init {
    title = IdeBundle.message("dialog.title.untrusted.server.s.certificate")
    setOKButtonText(IdeBundle.message("show.details"))
    okAction.putValue(DEFAULT_ACTION, null)
    okAction.putValue(MAC_ACTION_ORDER, -10)
    setCancelButtonText(IdeBundle.message("button.reject"))
    cancelAction.putValue(DEFAULT_ACTION, true)
    cancelAction.putValue(MAC_ACTION_ORDER, 100)
    init()
  }

  private enum class CertificateError {
    EXPIRED, NOT_YET_VALID, UNTRUSTED_AUTHORITY, SELF_SIGNED
  }

  override fun createCenterPanel(): JComponent? {
    val panel = panel {
      row {
        var error: String? = null
        run breaking@{
          certificates.forEach {
            val errors = certificateErrorsMap[it]!!
            when {
              errors.contains(CertificateError.SELF_SIGNED) -> IdeBundle.message("ssl.certificate.error.self.signed")
              errors.contains(CertificateError.NOT_YET_VALID) -> IdeBundle.message("ssl.certificate.error.not.yet.valid")
              errors.contains(CertificateError.EXPIRED) -> IdeBundle.message("ssl.certificate.error.expired")
              errors.contains(CertificateError.UNTRUSTED_AUTHORITY) -> IdeBundle.message("ssl.certificate.error.untrusted.authority")
              else -> null
            }?.let {
              error = it
              return@breaking
            }
          }
        }
        icon(AllIcons.General.WarningDialog)
        val errorText = error?.let { IdeBundle.message("ssl.certificate.warning", it, if (hasUntrusted) IdeBundle.message("ssl.certificate.warning.plural") else IdeBundle.message("ssl.certificate.warning.singular")) }
                        ?: IdeBundle.message("ssl.certificate.warning.default")
        text(HtmlChunk.text(errorText).bold().toString()).applyToComponent {
          if (ScreenReader.isActive()) {
            isFocusable = true
          }
        }
      }
      if (remoteHost != null) {
        row(IdeBundle.message("ssl.certificate.server.address")) {
          text(remoteHost).align(AlignX.LEFT).applyToComponent {
            if (ScreenReader.isActive()) {
              isFocusable = true
            }
          }
        }
      }
      row {
        cell(tree).align(AlignX.FILL)
      }

      detailsCollapsibleRow = collapsibleGroup(IdeBundle.message("ssl.certificate.details")) {
        row {
          detailsPlaceholder = placeholder().align(AlignX.FILL)
        }
      }.apply {
        addExpandedListener {
          if (it) {
            CertificateWarningStatisticsCollector.detailsShown(expandedByButton)
            expandedByButton = false
          }
          if (it && !isDetailsShown) {
            setOKButtonText(IdeBundle.message("save.as.trusted.certificate"))
            isDetailsShown = true
            isOKActionEnabled = true
            updateDetails()
          }
          pack()
        }
      }
    }.withMinimumWidth(JBUIScale.scale(400))
      .withPreferredWidth(JBUIScale.scale(600))

    return JBScrollPane(panel, JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER).apply {
      border = JBUI.Borders.empty()
    }
  }

  override fun doOKAction() {
    if (!isDetailsShown) {
      expandedByButton = true
      detailsCollapsibleRow.expanded = true
    }
    else {
      val certificate = currentCertificate.certificate
      certificateProvider.selectedCertificate= certificate
      certificateProvider.isChainRemainUnsafe = certificate != certificates.first() && certificates.subList(0, certificates.indexOf(certificate) - 1).any { certificateErrorsMap[it]!!.isNotEmpty() }
      super.doOKAction()
      CertificateWarningStatisticsCollector.certificateAccepted()
    }
  }

  override fun doCancelAction() {
    super.doCancelAction()
    CertificateWarningStatisticsCollector.certificateRejected()
  }

  private fun createCertificateTree(): JTree {
    val certificatesTree =
      if ((hasUntrusted && certificates.size > 1) || certificateErrorsMap.filter { entry -> entry.value.isNotEmpty() }.size > 1) {
        val root = CheckedTreeNode("root").apply { isChecked = false }
        var lastNode = root
        certificates.reversed().forEach {
          val node = CheckedTreeNode(it).apply { isChecked = it == currentCertificate.certificate }
          lastNode.add(node)
          lastNode = node
        }

        @Suppress("HardCodedStringLiteral")
        val renderer = object : CheckboxTreeCellRenderer() {
          init {
            myIgnoreInheritance = true
          }

          override fun customizeRenderer(tree: JTree, value: Any, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) {
            super.customizeRenderer(tree, value, selected, expanded, leaf, row, hasFocus)
            val textAndColor = getTreeCellTextAndColor(value)
            textRenderer.append(textAndColor.first, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, textAndColor.second))
          }
        }
        val checkboxTree = object : CheckboxTree(renderer, root, CheckPolicy(false, false, false, false, true)) {
          override fun onNodeStateChanged(node: CheckedTreeNode?) {
            val userObject = node?.userObject as? X509Certificate ?: return
            if (node.isChecked) {
              unselectOtherNodes(root, node)
              currentCertificate = CertificateWrapper(userObject)
              updateDetails()
            }
            else {
              //prevent unselect a certificate
              node.isChecked = true
            }
            if (isDetailsShown) {
              isOKActionEnabled = true
            }
          }
        }
        checkboxTree
      }
      else {
        val root = DefaultMutableTreeNode("root")
        var lastNode = root
        certificates.reversed().forEach {
          val node = DefaultMutableTreeNode(it)
          lastNode.add(node)
          lastNode = node
        }
        val defaultTree = Tree(root)
        TreeUtil.selectInTree(lastNode, false, defaultTree)
        @Suppress("HardCodedStringLiteral")
        defaultTree.cellRenderer = object : TreeCellRenderer {
          override fun getTreeCellRendererComponent(tree: JTree?, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean): Component {
            val textAndColor = getTreeCellTextAndColor(value)
            val component = JBLabel(textAndColor.first)
            component.foreground = textAndColor.component2()
            return component
          }
        }
        defaultTree

      }

    TreeUtil.expandAll(certificatesTree)

    certificatesTree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    certificatesTree.isRootVisible = false

    certificatesTree.border = BorderFactory.createCompoundBorder(
      RoundedLineBorder(JBColor.border(), 10),
      JBUI.Borders.empty(3)
    )
    certificatesTree.accessibleContext.accessibleName = IdeBundle.message("ssl.certificate.tree.accessible.name")
    return certificatesTree
  }

  private fun unselectOtherNodes(root: CheckedTreeNode, selectedNode: CheckedTreeNode) {
    root.children().asSequence()
      .filterIsInstance<CheckedTreeNode>()
      .forEach { child ->
        if (child != selectedNode && child.isChecked) {
          child.isChecked = false
        }
        unselectOtherNodes(root = child, selectedNode = selectedNode)
      }
  }

  @NlsSafe
  private fun getTreeCellTextAndColor(value: Any?): Pair<String, Color> {
    val labelForeground = JBUI.CurrentTheme.Label.foreground()
    val userObject = (value as? DefaultMutableTreeNode)?.userObject as? X509Certificate ?: return Pair("", labelForeground)
    val certErrors = certificateErrorsMap[userObject]!!
    val errors = mutableListOf<String>()
    if (certErrors.contains(CertificateError.NOT_YET_VALID)) {
      errors.add(IdeBundle.message("label.certificate.not.yet.valid"))
    }
    else if (certErrors.contains(CertificateError.EXPIRED)) {
      errors.add(IdeBundle.message("label.certificate.expired"))
    }

    if (certErrors.contains(CertificateError.SELF_SIGNED)) {
      errors.add(IdeBundle.message("label.certificate.self.signed"))
    } else if (certErrors.contains(CertificateError.UNTRUSTED_AUTHORITY)) {
      val error = if (userObject != certificates.first()) IdeBundle.message("label.certificate.untrusted.authority")
      else IdeBundle.message("label.certificate.signed.by.untrusted.authority")
      errors.add(error)
    }
    val certificateName = getCertificateName(userObject)
    val errorText = errors.joinToString(" ${IdeBundle.message("label.certificate.and")} ")

    return if (errors.isNotEmpty()) Pair("$certificateName ($errorText)", errorColor) else Pair(certificateName, labelForeground)
  }

  private fun updateDetails() {
    val errors = certificateErrorsMap[currentCertificate.certificate] ?: emptyList()
    detailsPlaceholder.component = panel {
      panel {
        customizeSpacingConfiguration(object : IntelliJSpacingConfiguration() {
          override val verticalMediumGap: Int = 6
          override val verticalSmallGap: Int = 2
          override val verticalComponentGap: Int = 3
          override val horizontalIndent: Int = 20
        }) {
          row {
            label(IdeBundle.message("section.title.issued.to"))
          }.bottomGap(BottomGap.SMALL).topGap(TopGap.MEDIUM)
          indent {
            addPrincipalData(currentCertificate.subjectFields, true)
          }

          row {
            label(IdeBundle.message("section.title.issued.by"))
          }.bottomGap(BottomGap.SMALL).topGap(TopGap.MEDIUM)
          indent {
            addPrincipalData(currentCertificate.issuerFields, false)
          }

          row {
            label(IdeBundle.message("section.title.validity.period"))
          }.bottomGap(BottomGap.SMALL).topGap(TopGap.MEDIUM)
          indent {
            val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT)
            row(IdeBundle.message("label.valid.from")) {
              val notBefore = dateFormat.format(currentCertificate.notBefore)
              val hasError = errors.contains(CertificateError.NOT_YET_VALID)
              val text = if (hasError) "$notBefore (${IdeBundle.message("label.certificate.not.yet.valid")})" else "$notBefore"
              text(text).apply {
                if (hasError) {
                  component.foreground = errorColor
                }
              }
            }
            row(IdeBundle.message("label.valid.until")) {
              val notAfter = dateFormat.format(currentCertificate.notAfter)
              val hasError = errors.contains(CertificateError.EXPIRED)
              val text = if (hasError) "$notAfter (${IdeBundle.message("label.certificate.expired")})" else "$notAfter"
              text(text).apply {
                if (hasError) {
                  component.foreground = errorColor
                }
              }
            }
          }

          row {
            label(IdeBundle.message("section.title.fingerprints"))
          }.bottomGap(BottomGap.SMALL).topGap(TopGap.MEDIUM)
          @Suppress("HardCodedStringLiteral")
          indent {
            row("SHA-256:") {
              val pane = getTextPane(formatHex(currentCertificate.sha256Fingerprint))
              cell(pane).align(AlignX.FILL)
            }
            row("SHA-1:") {
              cell(getTextPane(formatHex(currentCertificate.sha1Fingerprint))).align(AlignX.FILL)
            }
          }
        }
      }
      row {
        val status = EditorNotificationPanel.Status.Warning
        val banner = InlineBanner(IdeBundle.message("trust.certificate.warning.details"), status)
        banner.showCloseButton(false)
        banner.addAction(IdeBundle.message("trust.certificate.warning.details.action")) {
          val certManager = CertificateManager.getInstance()
          val backgroundColor = UIUtil.getToolTipBackground()
          val foreground = UIUtil.getToolTipForeground()
          val component = ComponentUtil.findComponentsOfType(banner, LinkLabel::class.java).firstOrNull { it.isVisible } ?: banner
          JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder(IdeBundle.message("trust.certificate.warning.details.popup", certManager.cacertsPath, certManager.password), null, foreground, backgroundColor, null)
            .setBorderColor(backgroundColor)
            .setAnimationCycle(0)
            .createBalloon()
            .show(RelativePoint(component, Point()), Balloon.Position.above)
        }
        cell(banner)
          .align(AlignX.FILL)
          .applyToComponent {
            putClientProperty(DslComponentProperty.VISUAL_PADDINGS, UnscaledGaps.EMPTY)
          }
      }.topGap(TopGap.SMALL)
    }
  }

  private fun getCertificateName(cert: X509Certificate): String {
    return CertificateWrapper(cert).subjectFields[CommonField.COMMON_NAME.shortName] ?: cert.subjectX500Principal.name
  }

  private fun Panel.addPrincipalData(fields: Map<String, @NlsSafe String>, isIssuedTo: Boolean) {
    val errors = certificateErrorsMap[currentCertificate.certificate] ?: emptyList()
    val errorText = when {
      isIssuedTo && errors.contains(CertificateError.SELF_SIGNED) -> IdeBundle.message("label.certificate.self.signed")
      !isIssuedTo && errors.contains(CertificateError.UNTRUSTED_AUTHORITY) -> IdeBundle.message("label.certificate.untrusted.authority")
      else -> null
    }
    var isErrorHighlighted = false

    CommonField.entries.forEach { commonField ->
      val field = fields.entries.find { it.key == commonField.shortName }
      if (field == null) {
        return@forEach
      }
      row(commonField.longName + ":") {
        val errorFields = if (isIssuedTo) listOf(CommonField.ORGANIZATION_UNIT, CommonField.ORGANIZATION) else listOf(CommonField.COMMON_NAME)
        val errorCondition = errorText != null && !isErrorHighlighted && errorFields.contains(commonField)
        val text = if (errorCondition) field.value + " ($errorText)" else field.value
        text(text).apply {
          if (errorCondition) {
            component.foreground = errorColor
            isErrorHighlighted = true
          }
        }
      }
    }
  }

  fun formatHex(hex: String): String {
    if (CertificateWrapper.NOT_AVAILABLE == hex) return hex

    val builder = StringBuilder()
    var i = 0
    while (i < hex.length) {
      builder.append(hex, i, i + 2)
      builder.append(' ')
      i += 2
    }
    if (hex.isNotEmpty()) {
      builder.deleteCharAt(builder.length - 1)
    }
    return StringUtil.toUpperCase(builder.toString())
  }

  private fun getTextPane(@NlsSafe text: String): JTextPane {
    val pane = JTextPane()
    pane.isOpaque = false
    pane.isEditable = false
    pane.border = null
    pane.contentType = "text/plain"
    pane.text = text
    return pane
  }

  private fun getCertificateErrorsMap(): Map<X509Certificate, List<CertificateError>> {
    val result = certificates.associateWith { mutableListOf<CertificateError>() }.toMutableMap()
    val errorMessage = "unable to find valid certification path"
    val isPureUntrustedServer = try {
      manager.checkServerTrusted(certificates.toTypedArray(), authType)
      false
    }
    catch (e: CertificateException) {
      val hasPathError = e.message?.contains(errorMessage) == true
      if (!hasPathError) logger<CertificateWarningDialog>().info("Certificate validation message: ${e.message}")
      hasPathError
    }
    val reversedCertList = certificates.reversed()
    for (i in reversedCertList.indices) {
      val cert = reversedCertList[i]
      val model = CertificateWrapper(cert)
      val errors = result[cert]!!
      if (i == reversedCertList.size - 1 && result.values.flatten().isNotEmpty()) errors.add(CertificateError.UNTRUSTED_AUTHORITY)
      try {
        manager.checkServerTrusted(arrayOf(cert), authType)
      }
      catch (_: CertificateException) {
        if (isPureUntrustedServer && !model.isSelfSigned && !result.values.flatten().contains(CertificateError.UNTRUSTED_AUTHORITY)) {
          errors.add(CertificateError.UNTRUSTED_AUTHORITY)
        }
        else {
          if (model.isSelfSigned) errors.add(CertificateError.SELF_SIGNED)
          if (model.isNotYetValid) errors.add(CertificateError.NOT_YET_VALID)
          if (model.isExpired) errors.add(CertificateError.EXPIRED)
        }
      }
    }
    if (result.flatMap { it.value }.isEmpty()) result.put(certificates.first(), mutableListOf(CertificateError.UNTRUSTED_AUTHORITY))
    return result
  }
}

  