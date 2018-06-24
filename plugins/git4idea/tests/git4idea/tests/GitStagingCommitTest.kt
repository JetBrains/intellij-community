// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.tests

import com.intellij.openapi.util.registry.Registry

class GitStagingCommitTest : GitCommitTest() {
  override fun setUp() {
    super.setUp()
    Registry.get("git.force.commit.using.staging.area").setValue(true)
  }

  override fun tearDown() {
    try {
      Registry.get("git.force.commit.using.staging.area").resetToDefault()
    }
    finally {
      super.tearDown()
    }
  }
}