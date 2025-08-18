// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

interface LockReqsPatterns {
  val assertionMethods: Map<String, Map<String, LockType>>
  val lockAnnotations: Map<String, LockType>
  val edtRequiredPackages: Set<String>
  val edtRequiredClasses: Set<String>
  val asyncClasses: Set<String>
  val asyncMethods: Set<String>
  val messageBusClasses: Set<String>
  val messageBusMethods: Set<String>
  val safeSwingMethods: Set<String>
}

class DefaultLockReqsPatterns : LockReqsPatterns {

  override val assertionMethods: Map<String, Map<String, LockType>> = mapOf(
    "com.intellij.util.concurrency.ThreadingAssertions" to mapOf(
      "assertReadAccess" to LockType.READ,
      "assertWriteAccess" to LockType.WRITE,
      "assertWriteIntentReadAccess" to LockType.WRITE_INTENT,
      "assertEventDispatchThread" to LockType.EDT,
      "assertBackgroundThread" to LockType.BGT
    ),
    "com.intellij.openapi.application.Application" to mapOf(
      "assertReadAccessAllowed" to LockType.READ,
      "assertWriteAccessAllowed" to LockType.WRITE,
      "assertIsWriteThread" to LockType.WRITE,
      "assertIsDispatchThread" to LockType.EDT,
      "assertIsNonDispatchThread" to LockType.BGT
    )
  )

  override val lockAnnotations: Map<String, LockType> = mapOf(
    "com.intellij.util.concurrency.annotations.RequiresReadLock" to LockType.READ,
    "com.intellij.util.concurrency.annotations.RequiresWriteLock" to LockType.WRITE,
    "com.intellij.util.concurrency.annotations.RequiresEdt" to LockType.EDT,
    "com.intellij.util.concurrency.annotations.RequiresBackgroundThread" to LockType.BGT,
    "com.intellij.util.concurrency.annotations.RequiresReadLockAbsence" to LockType.NO_READ,
    "org.jetbrains.annotations.RequiresEdt" to LockType.EDT
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

  override val asyncClasses: Set<String> = setOf(
    "com.intellij.openapi.application.ApplicationManager",
    "java.util.concurrent.CompletableFuture",
    "java.util.concurrent.ExecutorService",
    "com.intellij.util.concurrency.AppExecutorUtil"
  )

  override val asyncMethods: Set<String> = setOf(
    "invokeLater", "invokeAndWait", "runInEdt",
    "submit", "execute", "executeOnPooledThread",
    "runAsync", "supplyAsync", "invokeLaterOnWriteThread"
  )

  override val messageBusClasses: Set<String> = setOf(
    "com.intellij.util.messages.MessageBus",
    "com.intellij.util.messages.MessageBusConnection"
  )

  override val messageBusMethods: Set<String> = setOf(
    "syncPublisher", "connect", "simpleConnect"
  )

  override val safeSwingMethods: Set<String> = setOf()
}
