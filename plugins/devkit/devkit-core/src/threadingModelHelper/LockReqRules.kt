// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import java.util.EnumSet

interface LockReqRules {
  val assertionMethods: Map<String, Map<String, ConstraintType>>
  val lockAnnotations: Map<String, ConstraintType>
  val edtRequiredPackages: Set<String>
  val edtRequiredClasses: Set<String>
  val asyncMethods: Set<Pair<String, String>>
  val messageBusClasses: Set<String>
  val messageBusSyncMethods: Set<String>
  val commonMethods: Set<String>
  val safeSwingMethods: Set<String>
  val commonClassesQualified: Set<String>
  val disposerUtilityClassFqn: String
  val disposableInterfaceFqn: String
  val disposeMethodNames: Set<String>

  /**
   * When analyzer configured to search for a subset of [ConstraintType] (via [AnalysisConfig.interestingConstraintTypes]),
   * and it meets a signature from [indifferent] that is matched with a subset of [AnalysisConfig.interestingConstraintTypes], this signature is ignored.
   * This is needed to save some performance on methods that guaranteedly won't have anything interesting inside them
   */
  val indifferent: Set<Pair<EnumSet<ConstraintType>, Signature>>
}

data class Signature(val classFqn: String, val methodName: String)

class BaseLockReqRules : LockReqRules {

  override val assertionMethods: Map<String, Map<String, ConstraintType>> = mapOf(
    "com.intellij.util.concurrency.ThreadingAssertions" to mapOf(
      "assertReadAccess" to ConstraintType.READ,
      "assertWriteAccess" to ConstraintType.WRITE,
      "assertWriteIntentReadAccess" to ConstraintType.WRITE_INTENT,
      "assertEventDispatchThread" to ConstraintType.EDT,
      "assertBackgroundThread" to ConstraintType.BGT
    ),
    "com.intellij.psi.SmartPsiElementPointer" to mapOf(
      "getElement" to ConstraintType.READ,
      "getPsiRange" to ConstraintType.READ,
      "getRange" to ConstraintType.READ,
    ),
    "com.intellij.psi.JavaPsiFacade" to mapOf(
      "findClass" to ConstraintType.READ
    ),
    "com.intellij.openapi.vfs.VirtualFileSystem" to mapOf(
      "refreshAndFindFileByPath" to ConstraintType.NO_READ
    ),
    "com.intellij.openapi.application.Application" to mapOf(
      "assertReadAccessAllowed" to ConstraintType.READ,
      "assertWriteAccessAllowed" to ConstraintType.WRITE,
      "assertIsWriteThread" to ConstraintType.WRITE,
      "assertIsDispatchThread" to ConstraintType.EDT,
      "assertIsNonDispatchThread" to ConstraintType.BGT
    )
  )

  override val lockAnnotations: Map<String, ConstraintType> = mapOf(
    "com.intellij.util.concurrency.annotations.RequiresReadLock" to ConstraintType.READ,
    "com.intellij.util.concurrency.annotations.RequiresWriteLock" to ConstraintType.WRITE,
    "com.intellij.util.concurrency.annotations.RequiresEdt" to ConstraintType.EDT,
    "com.intellij.util.concurrency.annotations.RequiresBackgroundThread" to ConstraintType.BGT,
    "com.intellij.util.concurrency.annotations.RequiresReadLockAbsence" to ConstraintType.NO_READ,
  )

  override val edtRequiredPackages: Set<String> = setOf(
    "javax.swing", "java.awt"
  )

  override val edtRequiredClasses: Set<String> = setOf(
    "javax.swing.JComponent", "javax.swing.JLabel", "javax.swing.JButton",
    "javax.swing.JPanel", "javax.swing.JFrame", "javax.swing.JTextField",
    "javax.swing.JTextArea", "javax.swing.JTable", "javax.swing.JTree",
    "javax.swing.JList", "java.awt.Component", "java.awt.Container",
    "java.awt.Window", "java.awt.Frame", "java.awt.Dialog"
  )

  override val asyncMethods: Set<Pair<String, String>> =
    listOf("invokeLater", "invokeAndWait", "invokeLaterOnWriteThread", "executeOnPooledThread").mapTo(mutableSetOf()) { "com.intellij.openapi.application.Application" to it } +
    listOf("invokeLaterIfNeeded", "invokeAndWaitIfNeeded").mapTo(mutableSetOf()) { "com.intellij.util.ui.UIUtil" to it }

  override val messageBusClasses: Set<String> = setOf(
    "com.intellij.util.messages.MessageBus"
  )

  override val messageBusSyncMethods: Set<String> = setOf(
    "syncPublisher"
  )

  override val commonClassesQualified: Set<String> = setOf()

    override val commonMethods: Set<String> = setOf(
    "equals", "hashCode", "toString", "getMessagesBus","getFile", "getService", "getName"
  )

  override val safeSwingMethods: Set<String> = setOf()

  override val disposerUtilityClassFqn: String = "com.intellij.openapi.util.Disposer"
  override val disposableInterfaceFqn: String = "com.intellij.openapi.Disposable"
  override val disposeMethodNames: Set<String> = setOf("dispose")

  override val indifferent: Set<Pair<EnumSet<ConstraintType>, Signature>> = setOf(
    THREAD_REQUIREMENTS to Signature("com.intellij.openapi.application.impl.ServerNonBlockingReadAction", "submit"),
    LOCK_REQUIREMENTS to Signature("com.intellij.openapi.vfs.VirtualFileManager", "findFileByUrl"),
    LOCK_REQUIREMENTS to Signature("com.intellij.openapi.vfs.impl.VirtualFileManagerImpl", "findFileByUrl")

  )
}
