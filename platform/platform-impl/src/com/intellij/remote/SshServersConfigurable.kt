// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.ui.components.JBTextField
import com.intellij.util.IconUtil
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
import javax.swing.JPanel

// TODO move this configurable to `ultimate`
class SshServersConfigurable(private val project: Project?) : MasterDetailsComponent() {
  init {
    initTree()
  }

  // TODO move the literal to bundle
  override fun getDisplayName(): String = "SSH Servers"

  override fun reset() {
    myRoot.removeAllChildren()

    for (extension in Extensions.getExtensions(SshConfigurables.EP_NAME)) {
      for (configurableBuilder in extension.getConfigurables(project)) {
        addNode(MyNode(configurableBuilder.build(TREE_UPDATER)), myRoot)
      }
    }

    super.reset()
  }

  override fun createActions(fromPopup: Boolean): MutableList<AnAction> {
    return arrayListOf(AddSshServerAction())
  }

  // TODO put text into bundle
  private inner class AddSshServerAction : AnAction("Add", null, IconUtil.getAddIcon()) {
    override fun actionPerformed(e: AnActionEvent) {
      // TODO use `RemoteCredentialsHolder`?
      val sshCredentials = RemoteCredentialsHolder()

      val node = MyNode(SshConfigurable(sshCredentials, TREE_UPDATER).apply { })
      addNode(node, myRoot)
      selectNodeInTree(node)
    }
  }
}

private class SshConfigurable(private val sshCredentials: RemoteCredentials, updateTree: Runnable)
  : NamedConfigurable<RemoteCredentials>(false, updateTree) {
  private val hostTextField = JBTextField()
  private val portTextField = JBTextField()
  private val userTextField = JBTextField()

  override fun getBannerSlogan(): String = displayName

  override fun isModified(): Boolean = false

  // TODO return either manually set name or generated one
  override fun getDisplayName(): String {
    val builder = StringBuilder()
    userTextField.text.let {
      if (it.isNotBlank()) {
        builder.append("$it@")
      }
    }
    hostTextField.text.let {
      if (it.isNotBlank()) {
        builder.append(it)
      }
      else {
        builder.append("<empty>")
      }
    }
    portTextField.text.let {
      if (it.isNotBlank()) {
        builder.append(":$it")
      }
    }
    return builder.toString()
  }

  // TODO implement
  override fun apply() = Unit

  // TODO implement custom naming
  override fun setDisplayName(name: String?): Unit = throw UnsupportedOperationException()

  override fun getEditableObject(): RemoteCredentials = sshCredentials

  override fun createOptionsPanel(): JComponent = FormBuilder()
    .setFormLeftIndent(UIUtil.DEFAULT_HGAP)
    .setHorizontalGap(UIUtil.DEFAULT_HGAP)
    .setVerticalGap(UIUtil.DEFAULT_VGAP)
    .addLabeledComponent("Host:", hostTextField)
    .addLabeledComponent("Port:", portTextField)
    .addLabeledComponent("User:", userTextField)
    .addComponentFillVertically(JPanel(), 0)
    .panel
}