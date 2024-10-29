// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.diff.impl.CacheDiffRequestProcessor
import com.intellij.diff.requests.NoDiffRequest
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class SingleFileDiffPreviewProcessor(project: Project, place: String) : CacheDiffRequestProcessor.Simple(project, place), DiffPreviewUpdateProcessor {

  fun updatePreview() {
    val state = UIUtil.isShowing(component)
    if (state) {
      refresh(false)
    }
    else {
      clear()
    }
  }

  override fun clear() = applyRequest(NoDiffRequest.INSTANCE, false, null)
  override fun refresh(fromModelRefresh: Boolean) = updateRequest()
}
