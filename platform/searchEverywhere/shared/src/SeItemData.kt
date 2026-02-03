// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.SemanticSearchEverywhereContributor
import com.intellij.openapi.application.readAction
import com.intellij.platform.searchEverywhere.impl.SeItemEntity
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.platform.searchEverywhere.providers.computeCatchingOrNull
import fleet.kernel.DurableRef
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

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

    if (item is SeLegacyItem) {
      computeCatchingOrNull(true, { e -> "Couldn't add language info (${providerId.value}): $e" }) {
        PSIPresentationBgRendererWrapper.toPsi(item.rawObject)?.let {
          readAction {
            additionalInfo[SeItemDataKeys.PSI_LANGUAGE_ID] = it.language.id
          }
        }
      }

      computeCatchingOrNull(true, { e -> "Couldn't add isSemantic info (${providerId.value}): $e" }) {
        val element = (item.rawObject as? PSIPresentationBgRendererWrapper.ItemWithPresentation<*>)?.item
                      ?: item.rawObject
        val contributor = (item.contributor as? PSIPresentationBgRendererWrapper)?.delegate ?: item.contributor
        val isSemanticElement = (contributor as? SemanticSearchEverywhereContributor)?.isElementSemantic(element) ?: false
        additionalInfo[SeItemDataKeys.IS_SEMANTIC] = isSemanticElement.toString()
      }
    }

    return SeItemDataImpl(uuid, providerId, item.weight(), item.presentation(), emptyList(), additionalInfo, entityRef)
  }
}

@Serializable
@ApiStatus.Internal
class SeItemDataImpl internal constructor(
  override val uuid: String,
  override val providerId: SeProviderId,
  override val weight: Int,
  override val presentation: SeItemPresentation,
  override val uuidsToReplace: List<String>,
  override val additionalInfo: Map<String, String>,
  private val itemRef: DurableRef<SeItemEntity>,
): SeItemData {
  val isCommand: Boolean get() = additionalInfo[SeItemDataKeys.IS_COMMAND]?.toBoolean() == true

  override fun fetchItemIfExists(): SeItem? {
    return itemRef.derefOrNull()?.findItemOrNull()
  }

  fun withUuidToReplace(uuidToReplace: List<String>): SeItemDataImpl {
    return SeItemDataImpl(uuid, providerId, weight, presentation, uuidToReplace, additionalInfo, itemRef)
  }

  fun withPresentation(presentation: SeItemPresentation): SeItemDataImpl {
    return SeItemDataImpl(uuid, providerId, weight, presentation, uuidsToReplace, additionalInfo, itemRef)
  }

  fun contentEquals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SeItemDataImpl) return false

    return presentation.contentEquals(other.presentation)
  }
}

@get:ApiStatus.Internal
val SeItemData.isCommand: Boolean get() = (this as SeItemDataImpl).isCommand

@ApiStatus.Internal
fun SeItemData.contentEquals(other: Any?): Boolean = (this as SeItemDataImpl).contentEquals(other)

@ApiStatus.Internal
fun SeItemData.withUuidToReplace(uuidToReplace: List<String>): SeItemData = (this as SeItemDataImpl).withUuidToReplace(uuidToReplace)

@ApiStatus.Internal
fun SeItemData.withPresentation(presentation: SeItemPresentation): SeItemData = (this as SeItemDataImpl).withPresentation(presentation)
