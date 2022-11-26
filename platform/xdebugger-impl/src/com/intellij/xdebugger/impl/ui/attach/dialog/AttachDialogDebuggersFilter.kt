package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.attach.XAttachPresentationGroup
import com.intellij.xdebugger.impl.ui.attach.dialog.extensions.XAttachTreeDebuggersPresentationProvider
import org.jetbrains.annotations.Nls

interface AttachDialogDebuggersFilter {
  fun canBeAppliedTo(presentationGroups: Set<XAttachPresentationGroup<*>>): Boolean
  @Nls fun getDisplayText(): String
  fun getPersistentKey(): String
}

class AttachDialogDebuggersFilterByGroup(private val group: XAttachPresentationGroup<*>): AttachDialogDebuggersFilter {
  override fun canBeAppliedTo(presentationGroups: Set<XAttachPresentationGroup<*>>): Boolean = presentationGroups.contains(group)

  override fun getDisplayText(): String = (group as? XAttachTreeDebuggersPresentationProvider)?.getDebuggersShortName() ?: group.groupName

  override fun getPersistentKey(): String = group::class.java.simpleName
}

object AttachDialogAllDebuggersFilter: AttachDialogDebuggersFilter {
  override fun canBeAppliedTo(presentationGroups: Set<XAttachPresentationGroup<*>>): Boolean = true

  override fun getDisplayText(): String = XDebuggerBundle.message("xdebugger.all.debuggers.messages")

  override fun getPersistentKey(): String = "ALL_DEBUGGERS"
}