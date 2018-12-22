// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.runanything

class GitAllRunAnythingProvider: GitRunAnythingProviderBase() {

  override fun getHelpCommand(): String {
    return "gitall"
  }

  override fun getHelpDescription(): String {
    return "Runs Git command in all repositories"
  }
}