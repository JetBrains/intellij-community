// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class SearchEverywhereFrontendService {

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SearchEverywhereFrontendService = project.getService(SearchEverywhereFrontendService::class.java)
  }
}