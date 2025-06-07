// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UIWizardUtil")

package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.ProjectBuilder
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.AbstractNewProjectWizardBuilder.Companion.addPostCommitAction
import com.intellij.ide.wizard.AbstractNewProjectWizardBuilder.Companion.commitByBuilder
import com.intellij.ide.wizard.AbstractNewProjectWizardBuilder.Companion.postCommitByBuilder
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.util.minimumWidth
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CancellationException
import javax.swing.JLabel


@get:ApiStatus.Internal
val WizardContext.projectOrDefault: Project get() = project ?: ProjectManager.getInstance().defaultProject

@ApiStatus.Internal
@ApiStatus.ScheduledForRemoval
@Deprecated(
  message = "Use NewProjectWizardChainStep.nextStep instead",
  replaceWith = ReplaceWith(
    "nextStep(f1).nextStep(f2)",
    "com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep"
  )
)
fun <T1, T2, T3> T1.chain(f1: (T1) -> T2, f2: (T2) -> T3): NewProjectWizardStep
  where T1 : NewProjectWizardStep, T2 : NewProjectWizardStep, T3 : NewProjectWizardStep {
  return nextStep(f1)
    .nextStep(f2)
}

@ApiStatus.Internal
@ApiStatus.ScheduledForRemoval
@Deprecated(
  message = "Use NewProjectWizardChainStep.nextStep instead",
  replaceWith = ReplaceWith(
    "nextStep(f1).nextStep(f2).nextStep(f3)",
    "com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep"
  )
)
fun <T1, T2, T3, T4> T1.chain(f1: (T1) -> T2, f2: (T2) -> T3, f3: (T3) -> T4): NewProjectWizardStep
  where T1 : NewProjectWizardStep, T2 : NewProjectWizardStep, T3 : NewProjectWizardStep, T4 : NewProjectWizardStep {
  return nextStep(f1)
    .nextStep(f2)
    .nextStep(f3)
}

/**
 * A little bit hacky solution, looking forward to a better idea
 */
@ApiStatus.Experimental
@ApiStatus.Internal
fun DialogPanel.setMinimumWidthForAllRowLabels(width: Int) {
  UIUtil.uiTraverser(this).asSequence()
    .filterIsInstance<JLabel>()
    .filter { isRowLabel(it) }
    .forEach { it.minimumWidth = width }
}

private fun isRowLabel(label: JLabel): Boolean {
  val layout = (label.parent as? DialogPanel)?.layout as? GridLayout
  if (layout == null) {
    return false
  }
  val constraints = layout.getConstraints(label)
  return label.getClientProperty(DslComponentProperty.ROW_LABEL) == true && constraints != null && constraints.gaps.left == 0
}

fun DialogPanel.withVisualPadding(topField: Boolean = false): DialogPanel {
  val top = if (topField) 20 else 15
  border = IdeBorderFactory.createEmptyBorder(JBInsets(top, 20, 20, 20))
  return this
}

/**
 * Notifies user-visible error if [execution] is failed.
 */
@ApiStatus.Experimental
inline fun NewProjectWizardStep.setupProjectSafe(
  project: Project,
  errorMessage: @NlsContexts.DialogMessage String,
  execution: () -> Unit
) {
  try {
    execution()
  }
  catch (ex: CancellationException) {
    throw ex
  }
  catch (ex: Exception) {
    logger<NewProjectWizardStep>().error(errorMessage, ex)

    invokeAndWaitIfNeeded {
      Messages.showErrorDialog(
        project,
        errorMessage + "\n" + ex.message.toString(),
        UIBundle.message("error.project.wizard.new.project.title", context.isCreatingNewProjectInt)
      )
    }
  }
}

/**
 * Schedules activity executed on the pooled thread after the project is opened.
 * The runnable will be executed in the current thread if the project is already opened.
 *
 * Note: The target project can be changed if the created project is attached to the multi-project workspace.
 * Therefore, use the project, from the [callback] parameter.
 *
 * @see ProjectBuilder.postCommit
 * @see StartupManager.runAfterOpened
 */
@ApiStatus.Experimental
fun NewProjectWizardStep.runAfterOpened(project: Project, callback: (Project) -> Unit) {
  addPostCommitAction(callback)
  // The StartupManager#runAfterOpened callback will be skipped, in case of attaching to the multi-project workspace.
  StartupManager.getInstance(project).runAfterOpened { callback(project) }
}

/**
 * Bridges the [ProjectBuilder] and [NewProjectWizardStep] abstractions.
 *
 * @param builder is a legacy abstraction for defining a new project wizard UI and project generator.
 */
@ApiStatus.Internal
@ApiStatus.Obsolete
fun NewProjectWizardStep.setupProjectFromBuilder(project: Project, builder: ProjectBuilder): Module? {
  return commitByBuilder(builder, project).firstOrNull()
    .also { postCommitByBuilder(builder) }
}
