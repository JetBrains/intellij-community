// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.SemanticSearchEverywhereContributor
import com.intellij.openapi.application.readAction
import com.intellij.platform.searchEverywhere.impl.SeItemEntity
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.providers.computeCatchingOrNull
import com.intellij.platform.searchEverywhere.providers.target.SeTargetItem
import com.intellij.platform.searchEverywhere.providers.target.SeTargetPresentableItem
import com.jetbrains.rhizomedb.DbContext
import fleet.kernel.DurableRef
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.cancellation.CancellationException

/**
 * Serializable data interface for search everywhere results
 * see [SeItem]
 * see [SeItemDataFactory]
 */
@Serializable
@ApiStatus.Experimental
sealed interface SeItemData {
  val uuid: String
  val providerId: SeProviderId
  val weight: Int
  val presentation: SeItemPresentation
  val uuidsToReplace: List<String>
  val additionalInfo: Map<String, String>

  fun fetchItemIfExists(): SeItem?
}

/**
 * Factory for creating [SeItemData] instances
 */
@ApiStatus.Experimental
class SeItemDataFactory {
  suspend fun createItemData(
    session: SeSession,
    uuid: String,
    item: SeItem,
    providerId: SeProviderId,
    additionalInfo: Map<String, String>,
  ): SeItemData? {
    val entityRef = SeItemEntity.createWith(session, item) ?: return null
    val additionalInfo = additionalInfo.toMutableMap()

    // The language holds for every item, because it comes out of the raw object.
    computeCatchingOrNull(true, { e -> "Couldn't add language info (${providerId.value}): $e" }) {
      readAction {
        PSIPresentationBgRendererWrapper.toPsi(item.rawObject)?.let {
          additionalInfo[SeItemDataKeys.PSI_LANGUAGE_ID] = it.language.id
        }
      }
    }

    if (item is SeLegacyItem) {
      additionalInfo[SeItemDataKeys.PROVIDER_SORT_WEIGHT] = item.contributor.sortWeight.toString()

      // A target provider runs a goto model, so it never returns a semantic element. Only a legacy
      // item needs this key, and it needs the contributor to answer.
      computeCatchingOrNull(true, { e -> "Couldn't add isSemantic info (${providerId.value}): $e" }) {
        val element = (item.rawObject as? PSIPresentationBgRendererWrapper.ItemWithPresentation<*>)?.item
                      ?: item.rawObject
        val contributor = (item.contributor as? PSIPresentationBgRendererWrapper)?.delegate ?: item.contributor
        val isSemanticElement = (contributor as? SemanticSearchEverywhereContributor)?.isElementSemantic(element) ?: false
        additionalInfo[SeItemDataKeys.IS_SEMANTIC] = isSemanticElement.toString()
      }

    }

    if ((item as? SeTargetItem)?.isExactMatch == true || (item as? SeTargetPresentableItem)?.isExactMatch == true) {
      additionalInfo[SeItemDataKeys.IS_EXACT_MATCH] = true.toString()
    }

    return SeItemDataImpl(uuid, providerId, SeComposedWeight.from(item), item.presentation(), emptyList(), additionalInfo, entityRef)
  }
}

@Serializable
@ApiStatus.Internal
class SeItemDataImpl internal constructor(
  override val uuid: String,
  override val providerId: SeProviderId,
  val composedWeight: SeComposedWeight,
  override val presentation: SeItemPresentation,
  override val uuidsToReplace: List<String>,
  override val additionalInfo: Map<String, String>,
  private val itemRef: DurableRef<SeItemEntity>,
): SeItemData {
  override val weight: Int get() = composedWeight.first
  val isCommand: Boolean get() = additionalInfo[SeItemDataKeys.IS_COMMAND]?.toBoolean() == true
  val isSemantic: Boolean get() = additionalInfo[SeItemDataKeys.IS_SEMANTIC]?.toBoolean() == true
  val isExactMatch: Boolean get() = additionalInfo[SeItemDataKeys.IS_EXACT_MATCH]?.toBoolean() == true

  override fun fetchItemIfExists(): SeItem? =
    try {
      itemRef.derefOrNull()?.findItemOrNull()
    }
    catch (e: CancellationException) {
      // `poison` is the Throwable the read tripped over; for the match-invalidation case it is a
      // `fleet.kernel.rete.UnsatisfiedMatchException` ("match invalidated by rete"). It also serves
      // as the discriminator: a non-poisoned context means this is a real cancellation -> rethrow.
      val poison = DbContext.threadBoundOrNull?.poison ?: throw e
      SeLog.warn("Couldn't fetch SE item ${providerId.value}/$uuid, DB context is poisoned: $poison")
      null
    }

  fun withUuidToReplace(uuidToReplace: List<String>): SeItemDataImpl {
    return SeItemDataImpl(uuid, providerId, composedWeight, presentation, uuidToReplace, additionalInfo, itemRef)
  }

  fun withPresentation(presentation: SeItemPresentation): SeItemDataImpl {
    return SeItemDataImpl(uuid, providerId, composedWeight, presentation, uuidsToReplace, additionalInfo, itemRef)
  }

  /** Replaces the first component of [composedWeight] with [weight], and keeps the other components. */
  fun withWeight(weight: Int): SeItemDataImpl {
    val components = composedWeight.components.drop(1) + SeWeightComponent(weight)
    return SeItemDataImpl(uuid, providerId, SeComposedWeight.of(components), presentation, uuidsToReplace, additionalInfo, itemRef)
  }

  fun withWeight(weight: SeComposedWeight): SeItemDataImpl {
    return SeItemDataImpl(uuid, providerId, weight, presentation, uuidsToReplace, additionalInfo, itemRef)
  }

  fun contentEquals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SeItemDataImpl) return false

    return presentation.contentEquals(other.presentation)
  }
}

@get:ApiStatus.Internal
val SeItemData.composedWeight: SeComposedWeight get() = (this as SeItemDataImpl).composedWeight

@get:ApiStatus.Internal
val SeItemData.isCommand: Boolean get() = (this as SeItemDataImpl).isCommand

@get:ApiStatus.Internal
val SeItemData.isSemantic: Boolean get() = (this as SeItemDataImpl).isSemantic

@get:ApiStatus.Internal
val SeItemData.isExactMatch: Boolean get() = (this as SeItemDataImpl).isExactMatch

@ApiStatus.Internal
fun SeItemData.contentEquals(other: Any?): Boolean = (this as SeItemDataImpl).contentEquals(other)

@ApiStatus.Internal
fun SeItemData.withUuidToReplace(uuidToReplace: List<String>): SeItemData = (this as SeItemDataImpl).withUuidToReplace(uuidToReplace)

@ApiStatus.Internal
fun SeItemData.withPresentation(presentation: SeItemPresentation): SeItemData = (this as SeItemDataImpl).withPresentation(presentation)

@ApiStatus.Internal
fun SeItemData.withWeight(weight: Int): SeItemData = (this as SeItemDataImpl).withWeight(weight)

@ApiStatus.Internal
fun SeItemData.withWeight(weight: SeComposedWeight): SeItemData = (this as SeItemDataImpl).withWeight(weight)
