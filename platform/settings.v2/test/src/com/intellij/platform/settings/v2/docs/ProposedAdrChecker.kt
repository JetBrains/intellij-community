// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.docs

import java.nio.file.Path

/**
 * Finds the decision records (ADRs) in one folder that are still `Proposed`.
 *
 * A `Proposed` ADR is normal during the work on a branch. The user accepts the ADR before the branch goes to master.
 * `SettingsDocsTest` runs this check on every run, so a run during the work also reports each `Proposed` ADR.
 *
 * @param adrDir the folder that holds the `NNNN-<slug>.md` files.
 */
class ProposedAdrChecker(private val adrDir: Path) {
  /** Returns one problem for each `Proposed` ADR. */
  fun findProblems(): List<DocProblem> =
    readAdrFiles(adrDir).filter { it.statusWord == PROPOSED_STATUS }.map {
      DocProblem(it.file, it.statusLine, "ADR ${it.number} is $PROPOSED_STATUS. Accept it before it goes to master")
    }
}
