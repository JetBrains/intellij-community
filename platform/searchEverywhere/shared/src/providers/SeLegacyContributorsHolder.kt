// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSession
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.asRef
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.Entity
import com.jetbrains.rhizomedb.EntityType
import com.jetbrains.rhizomedb.RefFlags
import com.jetbrains.rhizomedb.entities
import com.jetbrains.rhizomedb.exists
import com.jetbrains.rhizomedb.get
import fleet.kernel.DurableEntityType
import fleet.kernel.DurableRef
import fleet.kernel.change
import fleet.kernel.ref
import fleet.kernel.rebase.shared
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
sealed interface SeLegacyContributorsRef {
  fun findContributorsOrNull(): SeLegacyContributors?
}

@ApiStatus.Internal
@Serializable
class SeLegacyContributorsRefImpl(val ref: DurableRef<SeLegacyContributorsEntity>) : SeLegacyContributorsRef {
  override fun findContributorsOrNull(): SeLegacyContributors? = ref.derefOrNull()?.findContributorsOrNull()

  companion object {
    suspend fun create(session: SeSession, contributors: SeLegacyContributors): SeLegacyContributorsRefImpl? =
      SeLegacyContributorsEntity.createWith(session, contributors)?.let { SeLegacyContributorsRefImpl(it) }
  }
}

@ApiStatus.Internal
class SeLegacyContributors(
  val allTab: Map<SeProviderId, SearchEverywhereContributor<Any>>,
  val separateTab: Map<SeProviderId, SearchEverywhereContributor<Any>>,
)

@ApiStatus.Internal
@Serializable
class SeLegacyContributorsEntity(override val eid: EID) : Entity {
  fun findContributorsOrNull(): SeLegacyContributors? {
    return entities(SeLegacyContributorsEntityHolder.ContributorsEntity, this).firstOrNull()?.contributors
  }

  @ApiStatus.Internal
  companion object : DurableEntityType<SeLegacyContributorsEntity>(SeLegacyContributorsEntity::class, ::SeLegacyContributorsEntity) {
    private val Session = requiredRef<SeSessionEntity>("session", RefFlags.CASCADE_DELETE_BY)

    suspend fun createWith(session: SeSession, contributors: SeLegacyContributors): DurableRef<SeLegacyContributorsEntity>? {
      @Suppress("DEPRECATION")
      return withKernel {
        val session = session.asRef().derefOrNull() ?: return@withKernel null

        change {
          val entity = shared {
            if (!session.exists()) return@shared null

            SeLegacyContributorsEntity.new {
              it[Session] = session
            }
          } ?: return@change null

          SeLegacyContributorsEntityHolder.new {
            it[SeLegacyContributorsEntityHolder.Contributors] = contributors
            it[SeLegacyContributorsEntityHolder.ContributorsEntity] = entity
          }

          entity
        }?.ref()
      }
    }
  }
}

@ApiStatus.Internal
class SeLegacyContributorsEntityHolder(override val eid: EID) : Entity {
  val contributors: SeLegacyContributors?
    get() = this[Contributors] as? SeLegacyContributors

  @ApiStatus.Internal
  companion object : EntityType<SeLegacyContributorsEntityHolder>(SeLegacyContributorsEntityHolder::class.java.name, "com.intellij", {
    SeLegacyContributorsEntityHolder(it)
  }) {
    internal val Contributors = requiredTransient<Any>("contributors")
    internal val ContributorsEntity = requiredRef<SeLegacyContributorsEntity>("contributorsEntity", RefFlags.CASCADE_DELETE_BY)
  }
}