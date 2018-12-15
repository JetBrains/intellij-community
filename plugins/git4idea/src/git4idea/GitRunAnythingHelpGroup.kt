// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea

import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider
import com.intellij.ide.actions.runAnything.groups.RunAnythingHelpGroup

class GitRunAnythingHelpGroup : RunAnythingHelpGroup<GitRunAnythingProviderBase>() {

  override fun getTitle(): String {
    return "git"
  }

  override fun getProviders(): Collection<GitRunAnythingProviderBase> {
    return RunAnythingProvider.EP_NAME.extensionList.filterIsInstance(GitRunAnythingProviderBase::class.java)
  }
}
