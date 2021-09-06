// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import javax.swing.Icon

class NewWizardEmptyModuleBuilder : NewWizardModuleBuilder(NewProjectWizardBaseStep.Factory()) {

  override fun getModuleType() = NewWizardEmptyModuleType.INSTANCE
  override fun getGroupName() = DEFAULT_GROUP
  override fun getNodeIcon(): Icon? = null
}