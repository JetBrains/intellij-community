// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history

import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.newvfs.impl.FilePathInterner
import com.intellij.util.containers.Interner
import com.intellij.vcs.log.impl.VcsFileStatusInfo
import git4idea.history.GitLogParser.GitLogOption

internal interface GitLogRecordBuilder<R : GitLogRecord> {
  fun addPath(type: Change.Type, firstPath: String, secondPath: String?)
  fun build(options: MutableMap<GitLogOption, String>, supportsRawBody: Boolean): R
  fun clear()
}

internal class DefaultGitLogRecordBuilder : GitLogRecordBuilder<GitLogRecord> {
  override fun addPath(type: Change.Type, firstPath: String, secondPath: String?) {
    throw UnsupportedOperationException("Can not add paths to GitLogRecord")
  }

  override fun build(options: MutableMap<GitLogOption, String>, supportsRawBody: Boolean): GitLogRecord {
    return GitLogRecord(options, supportsRawBody)
  }

  override fun clear() {
  }
}

internal open class DefaultGitLogFullRecordBuilder : GitLogRecordBuilder<GitLogFullRecord> {
  internal var statuses: MutableList<VcsFileStatusInfo> = mutableListOf()
    private set

  override fun build(options: MutableMap<GitLogOption, String>, supportsRawBody: Boolean): GitLogFullRecord {
    return GitLogFullRecord(options, statuses, supportsRawBody)
  }

  override fun addPath(type: Change.Type, firstPath: String, secondPath: String?) {
    statuses.add(VcsFileStatusInfo(type, firstPath, secondPath))
  }

  override fun clear() {
    statuses = mutableListOf()
  }
}

internal class InternedGitLogRecordBuilder : DefaultGitLogFullRecordBuilder() {
  private val pathInterner: Interner<CharSequence> = FilePathInterner()

  override fun addPath(type: Change.Type, firstPath: String, secondPath: String?) {
    val info = VcsFileStatusInfo(type, pathInterner.intern(firstPath), secondPath?.let { pathInterner.intern(it) })
    statuses.add(info)
  }
}

