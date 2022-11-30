// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model.impl

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsViewModel

class GHPRDetailsViewModelImpl(
  detailsModel: GHPRDetailsModel
) : GHPRDetailsViewModel {
  private val _titleState: MutableStateFlow<String> = MutableStateFlow(detailsModel.title)
  override val titleState: StateFlow<String> = _titleState.asStateFlow()

  private val _numberState: MutableStateFlow<String> = MutableStateFlow(detailsModel.number)
  override val numberState: StateFlow<String> = _numberState.asStateFlow()

  private val _urlState: MutableStateFlow<String> = MutableStateFlow(detailsModel.url)
  override val urlState: StateFlow<String> = _urlState.asStateFlow()

  private val _descriptionState: MutableStateFlow<String> = MutableStateFlow(detailsModel.description)
  override val descriptionState: StateFlow<String> = _descriptionState.asStateFlow()

  init {
    detailsModel.addAndInvokeDetailsChangedListener {
      _titleState.value = detailsModel.title
      _numberState.value = detailsModel.number
      _urlState.value = detailsModel.url
      _descriptionState.value = detailsModel.description
    }
  }
}