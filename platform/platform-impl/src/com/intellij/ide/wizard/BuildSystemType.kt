// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.util.NlsContexts

interface BuildSystemType : NewProjectWizardStep.Factory {
  val name: @NlsContexts.Label String
}