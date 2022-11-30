// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import kotlinx.coroutines.flow.StateFlow

interface GHPRDetailsViewModel {
  val titleState: StateFlow<String>
  val numberState: StateFlow<String>
  val urlState: StateFlow<String>
  val descriptionState: StateFlow<String>
}