// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.rhizome.repository

import com.intellij.platform.project.ProjectEntity
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import com.intellij.vcs.git.shared.rpc.GitReferencesSet
import com.jetbrains.rhizomedb.*
import fleet.kernel.DurableEntityType
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class GitRepositoryEntity(override val eid: EID) : Entity {
  val repositoryId: RepositoryId by RepositoryIdValue
  val project: ProjectEntity by Project
  val state: GitRepositoryStateEntity by State
  val favoriteRefs: GitRepositoryFavoriteRefsEntity by FavoriteRefs

  companion object : DurableEntityType<GitRepositoryEntity>(GitRepositoryEntity::class.java.name, "com.intellij", ::GitRepositoryEntity) {
    val RepositoryIdValue: Required<RepositoryId> = requiredValue("repositoryId", RepositoryId.serializer(), Indexing.UNIQUE)
    val Project: Required<ProjectEntity> = requiredRef("project")
    val State: Required<GitRepositoryStateEntity> = requiredRef("state", RefFlags.UNIQUE, RefFlags.CASCADE_DELETE)
    val FavoriteRefs: Required<GitRepositoryFavoriteRefsEntity> = requiredRef("favoriteRefs", RefFlags.UNIQUE, RefFlags.CASCADE_DELETE)

    fun inProject(projectEntity: ProjectEntity): Set<GitRepositoryEntity> = entities(Project, projectEntity)
  }
}

@ApiStatus.Internal
data class GitRepositoryStateEntity(override val eid: EID) : Entity {
  val repositoryId: RepositoryId by RepositoryIdValue
  val currentRef: String? by CurrentRef
  val refs: GitReferencesSet by RefrencesSet

  companion object : DurableEntityType<GitRepositoryStateEntity>(GitRepositoryStateEntity::class.java.name, "com.intellij", ::GitRepositoryStateEntity) {
    val RepositoryIdValue: Required<RepositoryId> = requiredValue("repositoryId", RepositoryId.serializer(), Indexing.INDEXED)
    val CurrentRef: Optional<String> = optionalValue("currentRef", String.serializer())
    val RefrencesSet: Required<GitReferencesSet> = requiredValue("referencesSet", GitReferencesSet.serializer())
  }
}

@ApiStatus.Internal
data class GitRepositoryFavoriteRefsEntity(override val eid: EID) : Entity {
  val repositoryId: RepositoryId by RepositoryIdValue
  val favoriteRefs: Set<String> by FavoriteRefs

  companion object : DurableEntityType<GitRepositoryFavoriteRefsEntity>(GitRepositoryFavoriteRefsEntity::class.java.name, "com.intellij", ::GitRepositoryFavoriteRefsEntity) {
    val RepositoryIdValue: Required<RepositoryId> = requiredValue("repositoryId", RepositoryId.serializer(), Indexing.INDEXED)
    val FavoriteRefs: Required<Set<String>> = requiredValue("favoriteRefs", SetSerializer(String.serializer()))
  }
}
