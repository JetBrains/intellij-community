/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.changes.patch.tool

import com.intellij.diff.requests.DiffRequest
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.util.NlsContexts

class PatchDiffRequest(
  val patch: TextFilePatch,
  private val windowTitle: @NlsContexts.DialogTitle String?,
  val contentTitle1: @NlsContexts.Label String?,
  val contentTitle2: @NlsContexts.Label String?
) : DiffRequest() {

  constructor(appliedPatch: TextFilePatch) : this(appliedPatch, null, null, null)

  override fun getTitle(): String? {
    return windowTitle
  }
}
