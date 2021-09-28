// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

class NewEmptyProjectBuilder : AbstractNewProjectWizardBuilder(NewProjectWizardBaseStep.Factory()) {
  override fun getModuleType() = NewEmptyProjectType.INSTANCE
  override fun getGroupName() = DEFAULT_GROUP
  override fun getPresentableName() = NewEmptyProjectType.INSTANCE.name
}