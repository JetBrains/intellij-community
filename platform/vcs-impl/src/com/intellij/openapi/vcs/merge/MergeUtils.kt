// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.merge

import com.intellij.diff.DiffVcsDataKeys
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.merge.MergeRequest
import com.intellij.diff.merge.ThreesideMergeRequest
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.util.ThreeSide
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.history.VcsRevisionNumber

object MergeUtils {

  @JvmStatic
  fun putRevisionInfos(request: MergeRequest, data: MergeData) {
    if (request is ThreesideMergeRequest) {
      val contents = request.contents
      putRevisionInfo(contents, data)
    }
  }

  @JvmStatic
  fun putRevisionInfos(request: DiffRequest, data: MergeData) {
    if (request is ContentDiffRequest) {
      val contents = request.contents
      if (contents.size == 3) {
        putRevisionInfo(contents, data)
      }
    }
  }

  private fun putRevisionInfo(contents: List<DiffContent>, data: MergeData) {
    for (side in ThreeSide.values()) {
      val content = side.select(contents)
      val filePath: FilePath? = side.select(data.CURRENT_FILE_PATH, data.ORIGINAL_FILE_PATH, data.LAST_FILE_PATH)
      val revision: VcsRevisionNumber? = side.select(data.CURRENT_REVISION_NUMBER, data.ORIGINAL_REVISION_NUMBER,
                                                     data.LAST_REVISION_NUMBER)
      if (filePath != null && revision != null) {
        content.putUserData(DiffVcsDataKeys.REVISION_INFO, Pair.create(filePath, revision))
      }
    }
  }
}