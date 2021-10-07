// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext

class NewProjectBuilder : AbstractNewProjectWizardBuilder() {
  override fun getModuleType() = NewProjectType.INSTANCE
  override fun getGroupName() = DEFAULT_GROUP
  override fun getPresentableName() = NewProjectType.INSTANCE.name

  override fun createStep(context: WizardContext) =
    NewProjectWizardBaseStep(context)
      .chain(::NewProjectWizardLanguageStep)
}
