// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

class NewModuleBuilder : AbstractNewProjectWizardBuilder(NewProjectWizardBaseStep.Factory(NewProjectWizardLanguageStep.Factory())) {
  override fun getModuleType() = NewModuleType.INSTANCE
  override fun getGroupName() = DEFAULT_GROUP
  override fun getPresentableName() = NewModuleType.INSTANCE.name
}
