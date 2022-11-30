package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.xdebugger.attach.XAttachHost
import com.intellij.xdebugger.attach.XAttachHostProvider
import com.intellij.xdebugger.attach.XAttachPresentationGroup
import org.jetbrains.annotations.Nls
import javax.swing.Icon


@Suppress("UNCHECKED_CAST")
data class AttachHostAndProvider(
  val host: XAttachHost,
  val provider: XAttachHostProvider<out XAttachHost>,
  val project: Project,
  val dataHolder: UserDataHolder) {

  @Nls
  fun getPresentation(): String {
    val presentationGroup = provider.presentationGroup as XAttachPresentationGroup<XAttachHost>
    return presentationGroup.getItemDisplayText(project, host, dataHolder)
  }

  fun getIcon(): Icon {
    val presentationGroup = provider.presentationGroup as XAttachPresentationGroup<XAttachHost>
    return presentationGroup.getItemIcon(project, host, dataHolder)
  }

  override fun equals(other: Any?): Boolean {
    if (other !is AttachHostAndProvider) {
      return false
    }
    return other.host == host
  }

  override fun hashCode(): Int {
    return host.hashCode()
  }
}