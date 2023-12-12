// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UIWizardUtil")

package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.ProjectBuilder
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.util.minimumWidth
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CancellationException
import javax.swing.JLabel


val WizardContext.projectOrDefault: Project get() = project ?: ProjectManager.getInstance().defaultProject

@Deprecated(
  message = "Use NewProjectWizardChainStep.nextStep instead",
  replaceWith = ReplaceWith(
    "nextStep(f1)",
    "com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep"
  )
)
fun <T1, T2> T1.chain(f1: (T1) -> T2): NewProjectWizardStep
  where T1 : NewProjectWizardStep, T2 : NewProjectWizardStep {
  return nextStep(f1)
}

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

@Deprecated(
  message = "Use NewProjectWizardChainStep.nextStep instead",
  replaceWith = ReplaceWith(
    "nextStep(f1).nextStep(f2).nextStep(f3).nextStep(f4)",
    "com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep"
  )
)
fun <T1, T2, T3, T4, T5> T1.chain(f1: (T1) -> T2, f2: (T2) -> T3, f3: (T3) -> T4, f4: (T4) -> T5): NewProjectWizardStep
  where T1 : NewProjectWizardStep, T2 : NewProjectWizardStep, T3 : NewProjectWizardStep, T4 : NewProjectWizardStep, T5 : NewProjectWizardStep {
  return nextStep(f1)
    .nextStep(f2)
    .nextStep(f3)
    .nextStep(f4)
}

@Deprecated("Use NewProjectWizardChainStep.nextStep instead")
fun stepSequence(first: NewProjectWizardStep, vararg rest: NewProjectWizardStep): NewProjectWizardStep {
  val steps = listOf(first) + rest
  return object : AbstractNewProjectWizardStep(first) {
    override fun setupUI(builder: Panel) {
      for (step in steps) {
        step.setupUI(builder)
      }
    }

    override fun setupProject(project: Project) {
      for (step in steps) {
        step.setupProject(project)
      }
    }
  }
}

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
fun NewProjectWizardStep.setupProjectSafe(
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
  catch (ex: ProcessCanceledException) {
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

@ApiStatus.Internal
fun whenProjectCreated(project: Project, action: () -> Unit) {
  if (ApplicationManager.getApplication().isUnitTestMode) {
    action()
  }
  else {
    StartupManager.getInstance(project).runAfterOpened {
      ApplicationManager.getApplication().invokeLater(action, project.disposed)
    }
  }
}

/**
 * This is a workaround for https://youtrack.jetbrains.com/issue/IDEA-286690
 */
@ApiStatus.Internal
fun NewProjectWizardStep.setupProjectFromBuilder(project: Project, builder: ProjectBuilder): Module? {
  val model = context.getUserData(NewProjectWizardStep.MODIFIABLE_MODULE_MODEL_KEY)
  return builder.commit(project, model)?.firstOrNull()
}

