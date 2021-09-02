// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext

class EmptyModuleStep(context: WizardContext) : NewModuleStep(context) {
  override val steps = listOf(Step(context))
}