// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history

import com.intellij.openapi.vcs.changes.Change
import com.intellij.vcs.log.impl.VcsFileStatusInfo
import git4idea.history.GitLogParser.GitLogOption

internal interface GitLogRecordBuilder {
  fun addPath(type: Change.Type, firstPath: String, secondPath: String?)
  fun build(options: MutableMap<GitLogOption, String>, supportsRawBody: Boolean): GitLogRecord
  fun clear()
}

internal class DefaultGitLogRecordBuilder : GitLogRecordBuilder {
  private var statuses: MutableList<VcsFileStatusInfo> = mutableListOf()

  override fun build(options: MutableMap<GitLogOption, String>, supportsRawBody: Boolean): GitLogRecord {
    return GitLogRecord(options, statuses, supportsRawBody)
  }

  override fun addPath(type: Change.Type, firstPath: String, secondPath: String?) {
    statuses.add(VcsFileStatusInfo(type, firstPath, secondPath))
  }

  override fun clear() {
    statuses = mutableListOf()
  }
}
