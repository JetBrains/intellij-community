// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext

class NewEmptyProjectBuilder : AbstractNewProjectWizardBuilder() {
  override fun getModuleType() = NewEmptyProjectType.INSTANCE
  override fun getGroupName() = DEFAULT_GROUP
  override fun getPresentableName() = NewEmptyProjectType.INSTANCE.name

  override fun createStep(context: WizardContext) = NewProjectWizardBaseStep(context)
}