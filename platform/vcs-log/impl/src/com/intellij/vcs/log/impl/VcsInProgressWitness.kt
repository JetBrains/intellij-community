// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.platform.backend.observation.MarkupBasedActivityInProgressWitness

class VcsInProgressWitness : MarkupBasedActivityInProgressWitness() {
  override val presentableName: String = "vcs-log"
}