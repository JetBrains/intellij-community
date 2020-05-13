// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update

import git4idea.config.GitSaveChangesPolicy
import git4idea.test.GitPlatformTest

abstract class GitUpdateBaseTest : GitPlatformTest() {

  private lateinit var originalPreservingPolicy : GitSaveChangesPolicy

  override fun setUp() {
    super.setUp()

    originalPreservingPolicy = settings.saveChangesPolicy
    settings.saveChangesPolicy = GitSaveChangesPolicy.STASH
  }

  override fun tearDown() {
    try {
      settings.saveChangesPolicy = originalPreservingPolicy
    }
    finally {
      super.tearDown()
    }
  }
}