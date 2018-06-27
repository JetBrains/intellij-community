// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package chm

import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import git4idea.checkin.GitCheckinEnvironment

class MyCommitProcess : GitCheckinEnvironment.OverridingCommitProcedure {

  override fun commit(changes: List<Change>, message: String) {

    // TODO deleted

    val ancestor = ChangesUtil.findCommonAncestor(changes)

    // get history for ancestor
    // find latest label "commit changes"
    // get all changes made after "commit changes"
    // extract refactorings from them


  }


}