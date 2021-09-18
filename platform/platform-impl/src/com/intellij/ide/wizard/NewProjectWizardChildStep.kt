// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

interface NewProjectWizardChildStep<P : NewProjectWizardStep> : NewProjectWizardStep {

  val parentStep: P

  interface Factory<P : NewProjectWizardStep> {

    fun createStep(parent: P): NewProjectWizardChildStep<P>
  }
}