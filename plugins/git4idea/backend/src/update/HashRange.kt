// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update

import com.intellij.vcs.log.Hash

/**
 * The same as [com.intellij.vcs.log.VcsLogRangeFilter.RefRange] but the limits are defined as Hashes of exact commits.
 */
data class HashRange(val start: Hash, val end: Hash)