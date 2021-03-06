// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.repo

import com.intellij.ide.impl.ProjectOriginInfoProvider
import com.intellij.util.io.exists
import java.nio.file.Path

class GitProjectOriginInfoProvider : ProjectOriginInfoProvider {
  override fun getOriginUrl(projectDir: Path): String? {
    val gitConfig = projectDir.resolve(".git/config")
    if (!gitConfig.exists()) return null
    return GitConfig.read(gitConfig.toFile()).parseRemotes().find { it.name == GitRemote.ORIGIN }?.firstUrl
  }
}