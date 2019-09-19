// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.repo

import com.google.common.hash.Hashing
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.jetcache.ProjectStateHashGenerator

class GitProjectStateHashGenerator : ProjectStateHashGenerator {
    override fun generateHash(project: Project): ByteArray {
        val repoManager = GitRepositoryManager.getInstance(project)
        val revisions = repoManager.repositories.joinToString("") { it.currentRevision ?: "" }
        if (revisions.isEmpty()) return byteArrayOf()

        val hash = Hashing.murmur3_32().hashString(revisions, Charsets.UTF_8)
        return hash.asBytes()
    }
}