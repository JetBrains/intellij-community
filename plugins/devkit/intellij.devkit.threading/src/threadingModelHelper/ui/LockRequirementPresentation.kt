// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.threading.threadingModelHelper.ui

import com.intellij.devkit.threading.DevkitThreadingBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.threadingModelHelper.ConstraintType
import org.jetbrains.idea.devkit.threadingModelHelper.ExecutionPath
import org.jetbrains.idea.devkit.threadingModelHelper.LockRequirement
import org.jetbrains.idea.devkit.threadingModelHelper.MethodCall
import org.jetbrains.idea.devkit.threadingModelHelper.RequirementReason
import javax.swing.Icon

internal fun requirementIcon(requirement: LockRequirement): Icon = when (requirement.requirementReason) {
  RequirementReason.ANNOTATION -> AllIcons.Nodes.Annotationtype
  RequirementReason.ASSERTION -> AllIcons.Debugger.ThreadAtBreakpoint
  RequirementReason.SWING_COMPONENT -> AllIcons.Nodes.Artifact
  RequirementReason.MESSAGE_BUS -> AllIcons.General.BalloonInformation
  RequirementReason.IMPLICIT -> AllIcons.General.Information
}

/**
 * A short label of the [requirement] for the path list, for example `@RequiresReadLock` or `assertIsEdt()`.
 */
internal fun requirementLabel(requirement: LockRequirement): @Nls String = when (requirement.requirementReason) {
  RequirementReason.ANNOTATION -> "@${annotationName(requirement.constraintType)}"
  RequirementReason.ASSERTION -> assertionName(requirement.constraintType)
  RequirementReason.SWING_COMPONENT -> DevkitThreadingBundle.message("requirement.label.swing.component")
  RequirementReason.MESSAGE_BUS -> DevkitThreadingBundle.message("requirement.label.message.bus")
  RequirementReason.IMPLICIT -> when (requirement.constraintType) {
    ConstraintType.READ -> DevkitThreadingBundle.message("requirement.label.implicit.read")
    ConstraintType.NO_READ -> DevkitThreadingBundle.message("requirement.label.implicit.no.read")
    ConstraintType.WRITE -> DevkitThreadingBundle.message("requirement.label.implicit.write")
    ConstraintType.WRITE_INTENT -> DevkitThreadingBundle.message("requirement.label.implicit.write.intent")
    ConstraintType.EDT -> DevkitThreadingBundle.message("requirement.label.implicit.edt")
    ConstraintType.BGT -> DevkitThreadingBundle.message("requirement.label.implicit.bgt")
  }
}

/**
 * A full description of the requirement of the [path] for the details panel.
 */
internal fun requirementDescription(path: ExecutionPath): @Nls String {
  val requirement = path.lockRequirement
  val type = when (requirement.constraintType) {
    ConstraintType.READ -> DevkitThreadingBundle.message("requirement.read")
    ConstraintType.NO_READ -> DevkitThreadingBundle.message("requirement.no.read")
    ConstraintType.WRITE -> DevkitThreadingBundle.message("requirement.write")
    ConstraintType.WRITE_INTENT -> DevkitThreadingBundle.message("requirement.write.intent")
    ConstraintType.EDT -> DevkitThreadingBundle.message("requirement.edt")
    ConstraintType.BGT -> DevkitThreadingBundle.message("requirement.bgt")
  }
  val reason = when (requirement.requirementReason) {
    RequirementReason.ANNOTATION -> DevkitThreadingBundle.message("requirement.reason.annotation")
    RequirementReason.ASSERTION -> DevkitThreadingBundle.message("requirement.reason.assertion")
    RequirementReason.SWING_COMPONENT -> DevkitThreadingBundle.message("requirement.reason.swing.component")
    RequirementReason.MESSAGE_BUS -> DevkitThreadingBundle.message("requirement.reason.message.bus")
    RequirementReason.IMPLICIT -> DevkitThreadingBundle.message("requirement.reason.implicit")
  }
  return if (path.isSpeculative) {
    DevkitThreadingBundle.message("details.requirement.speculative", type, reason)
  }
  else {
    DevkitThreadingBundle.message("details.requirement", type, reason)
  }
}

internal fun constraintTypeName(type: ConstraintType): @Nls String = when (type) {
  ConstraintType.READ -> DevkitThreadingBundle.message("constraint.type.read")
  ConstraintType.WRITE -> DevkitThreadingBundle.message("constraint.type.write")
  ConstraintType.WRITE_INTENT -> DevkitThreadingBundle.message("constraint.type.write.intent")
  ConstraintType.EDT -> DevkitThreadingBundle.message("constraint.type.edt")
  ConstraintType.BGT -> DevkitThreadingBundle.message("constraint.type.bgt")
  ConstraintType.NO_READ -> DevkitThreadingBundle.message("constraint.type.no.read")
}

/**
 * The short form of the [call] for the path list: the simple class name and the method name.
 */
internal fun shortCallText(call: MethodCall): @NlsSafe String {
  val cls = call.containingClassName?.substringAfterLast('.') ?: DevkitThreadingBundle.message("details.unknown.class")
  val methodName = if (call.methodName == cls) "<constructor>" else call.methodName
  return "$cls.$methodName"
}

/**
 * The full form of the [call] for the details panel: the qualified class name, the method name, and the source location.
 */
internal fun fullCallText(call: MethodCall): @NlsSafe String {
  val cls = call.containingClassName ?: DevkitThreadingBundle.message("details.unknown.class")
  return "$cls.${call.methodName}(${call.sourceLocation})"
}

/**
 * The [call] as a JVM stack frame line, for example `\tat com.example.Foo.bar(Foo.java:42)`.
 * The console filters of the IDE make this line a hyperlink to the source.
 */
internal fun stackFrameText(call: MethodCall): @NlsSafe String {
  val cls = call.containingClassName ?: DevkitThreadingBundle.message("details.unknown.class")
  val methodName = if (call.methodName == cls.substringAfterLast('.')) "<init>" else call.methodName
  return "\tat $cls.$methodName(${call.sourceLocation})"
}

/**
 * The plain-text form of the [path] for the clipboard.
 */
internal fun pathClipboardText(path: ExecutionPath): @NlsSafe String {
  val chain = path.methodChain.joinToString("\n") { fullCallText(it) }
  return "$chain\n-> ${annotationName(path.lockRequirement.constraintType)}"
}

private fun annotationName(type: ConstraintType): @NlsSafe String = when (type) {
  ConstraintType.READ -> "RequiresReadLock"
  ConstraintType.NO_READ -> "RequiresNoReadAccess"
  ConstraintType.WRITE -> "RequiresWriteLock"
  ConstraintType.WRITE_INTENT -> "RequiresWriteIntentLock"
  ConstraintType.EDT -> "RequiresEdt"
  ConstraintType.BGT -> "RequiresBackgroundThread"
}

private fun assertionName(type: ConstraintType): @NlsSafe String = when (type) {
  ConstraintType.READ -> "assertReadAccess()"
  ConstraintType.NO_READ -> "assertNoReadAccess()"
  ConstraintType.WRITE -> "assertWriteAccess()"
  ConstraintType.WRITE_INTENT -> "assertWriteIntentAllowed()"
  ConstraintType.EDT -> "assertIsEdt()"
  ConstraintType.BGT -> "assertIsBackgroundThread()"
}
