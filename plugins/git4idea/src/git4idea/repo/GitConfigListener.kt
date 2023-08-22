// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.repo

import com.intellij.util.messages.Topic

interface GitConfigListener {

  fun notifyConfigChanged(repository: GitRepository)

  companion object {
    @JvmField
    @Topic.ProjectLevel
    val TOPIC: Topic<GitConfigListener> =
      Topic(GitConfigListener::class.java, Topic.BroadcastDirection.NONE, true)
  }
}
