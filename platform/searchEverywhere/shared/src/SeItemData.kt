// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.SemanticSearchEverywhereContributor
import com.intellij.openapi.application.readAction
import com.intellij.platform.searchEverywhere.impl.SeItemId
import com.intellij.platform.searchEverywhere.impl.SeItemRegistry
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.platform.searchEverywhere.providers.computeCatchingOrNull
import com.intellij.platform.searchEverywhere.providers.target.SeTargetItem
import com.intellij.platform.searchEverywhere.providers.target.SeTargetPresentableItem
import com.intellij.platform.searchEverywhere.providers.target.SeTargetPresentableItemImpl
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

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
    val itemId = SeItemRegistry.getInstance().register(session, item) ?: return null
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

    (item as? SeItemWithDiagnostics)?.diagnostics?.let { additionalInfo[SeItemDataKeys.DIAGNOSTICS] = it }

    (item as? SeTargetPresentableItemImpl)?.index?.let { additionalInfo[SeItemDataKeys.TARGET_INDEX] = it.toString() }

    return SeItemDataImpl(uuid, providerId, SeComposedWeight.from(item), item.presentation(), emptyList(), additionalInfo, itemId)
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
  private val itemId: SeItemId,
): SeItemData {
  override val weight: Int get() = composedWeight.first
  val isCommand: Boolean get() = additionalInfo[SeItemDataKeys.IS_COMMAND]?.toBoolean() == true
  val isSemantic: Boolean get() = additionalInfo[SeItemDataKeys.IS_SEMANTIC]?.toBoolean() == true
  val isExactMatch: Boolean get() = additionalInfo[SeItemDataKeys.IS_EXACT_MATCH]?.toBoolean() == true
  val diagnostics: @NonNls String? get() = additionalInfo[SeItemDataKeys.DIAGNOSTICS]
  val targetIndex: Int? get() = additionalInfo[SeItemDataKeys.TARGET_INDEX]?.toIntOrNull()

  override fun fetchItemIfExists(): SeItem? = SeItemRegistry.getInstance().find(itemId)

  fun withUuidToReplace(uuidToReplace: List<String>): SeItemDataImpl {
    return SeItemDataImpl(uuid, providerId, composedWeight, presentation, uuidToReplace, additionalInfo, itemId)
  }

  fun withPresentation(presentation: SeItemPresentation): SeItemDataImpl {
    return SeItemDataImpl(uuid, providerId, composedWeight, presentation, uuidsToReplace, additionalInfo, itemId)
  }

  /** Replaces the first component of [composedWeight] with [weight], and keeps the other components. */
  fun withWeight(weight: Int): SeItemDataImpl {
    val components = composedWeight.components.drop(1) + SeWeightComponent(weight)
    return SeItemDataImpl(uuid, providerId, SeComposedWeight.of(components), presentation, uuidsToReplace, additionalInfo, itemId)
  }

  fun withWeight(weight: SeComposedWeight): SeItemDataImpl {
    return SeItemDataImpl(uuid, providerId, weight, presentation, uuidsToReplace, additionalInfo, itemId)
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

/** The diagnostic text of a [SeItemWithDiagnostics], or `null` when the item has none. */
@get:ApiStatus.Internal
val SeItemData.diagnostics: @NonNls String? get() = (this as SeItemDataImpl).diagnostics

/** The position of a target item in the order of its goto model, or `null` for another item. See [SeItemDataKeys.TARGET_INDEX]. */
@get:ApiStatus.Internal
val SeItemData.targetIndex: Int? get() = (this as SeItemDataImpl).targetIndex

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
