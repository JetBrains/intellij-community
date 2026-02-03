// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.xdebugger.attach.XAttachHost
import com.intellij.xdebugger.attach.XAttachHostProvider
import com.intellij.xdebugger.attach.XAttachPresentationGroup
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon


@ApiStatus.Internal
@Suppress("UNCHECKED_CAST")
data class AttachHostAndProvider(
  override val host: XAttachHost,
  val provider: XAttachHostProvider<out XAttachHost>,
  val project: Project,
  val dataHolder: UserDataHolder): AttachHostItem {

  override fun getId(): String {
    return getPresentation()
  }

  @Nls
  override fun getPresentation(): String {
    val presentationGroup = provider.getPresentationGroup() as XAttachPresentationGroup<XAttachHost>
    return presentationGroup.getItemDisplayText(project, host, dataHolder)
  }

  @Nls
  override fun toString(): String {
    return getPresentation()
  }

  override fun getIcon(): Icon {
    val presentationGroup = provider.getPresentationGroup() as XAttachPresentationGroup<XAttachHost>
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