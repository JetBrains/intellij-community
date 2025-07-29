// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.SemanticSearchEverywhereContributor
import com.intellij.openapi.application.readAction
import com.intellij.platform.searchEverywhere.impl.SeItemEntity
import com.intellij.platform.searchEverywhere.providers.computeCatchingOrNull
import fleet.kernel.DurableRef
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Serializable
@ApiStatus.Experimental
@ApiStatus.Internal
class SeItemData(
  val uuid: String,
  val providerId: SeProviderId,
  val weight: Int,
  val presentation: SeItemPresentation,
  val uuidsToReplace: List<String>,
  val additionalInfo: Map<String, String>,
  private val itemRef: DurableRef<SeItemEntity>,
) {
  fun fetchItemIfExists(): SeItem? {
    return itemRef.derefOrNull()?.findItemOrNull()
  }

  fun withUuidToReplace(uuidToReplace: List<String>): SeItemData {
    return SeItemData(uuid, providerId, weight, presentation, uuidToReplace, additionalInfo, itemRef)
  }

  @ApiStatus.Internal
  companion object {
    suspend fun createItemData(
      sessionRef: DurableRef<SeSessionEntity>,
      uuid: String,
      item: SeItem,
      providerId: SeProviderId,
      weight: Int,
      presentation: SeItemPresentation,
      additionalInfo: Map<String, String>,
      uuidToReplace: List<String>,
    ): SeItemData? {
      val entityRef = SeItemEntity.createWith(sessionRef, item) ?: return null
      val additionalInfo = additionalInfo.toMutableMap()

      if (item is SeLegacyItem) {
        computeCatchingOrNull(true, { e-> "Couldn't add language info (${providerId.value}): $e" }) {
          PSIPresentationBgRendererWrapper.toPsi(item.rawObject)?.let {
            readAction {
              additionalInfo[SeItemDataKeys.PSI_LANGUAGE_ID] = it.language.id
            }
          }
        }

        computeCatchingOrNull(true, { e-> "Couldn't add isSemantic info (${providerId.value}): $e" }) {
          val isSemanticElement = (item.contributor as? SemanticSearchEverywhereContributor)?.isElementSemantic(item.rawObject) ?: false
          additionalInfo[SeItemDataKeys.IS_SEMANTIC] = isSemanticElement.toString()
        }
      }

      return SeItemData(uuid, providerId, weight, presentation, uuidToReplace, additionalInfo, entityRef)
    }
  }
}
