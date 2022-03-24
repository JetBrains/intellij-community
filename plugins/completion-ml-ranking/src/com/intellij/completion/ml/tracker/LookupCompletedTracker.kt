// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.completion.ml.tracker

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.personalization.UserFactorDescriptions
import com.intellij.completion.ml.personalization.UserFactorStorage
import com.intellij.completion.ml.storage.MutableLookupStorage
import com.intellij.completion.ml.util.idString
import com.intellij.textMatching.PrefixMatchingType

/**
 * @author Vitaliy.Bibaev
 */
class LookupCompletedTracker : LookupFinishListener() {
    override fun cancelled(lookup: LookupImpl, canceledExplicitly: Boolean) {
        UserFactorStorage.applyOnBoth(lookup.project, UserFactorDescriptions.COMPLETION_FINISH_TYPE) { updater ->
            updater.fireLookupCancelled()
        }
    }

    override fun typedSelect(lookup: LookupImpl,
                             element: LookupElement) {
        UserFactorStorage.applyOnBoth(lookup.project, UserFactorDescriptions.COMPLETION_FINISH_TYPE) { updater ->
            updater.fireTypedSelectPerformed()
        }
    }

    override fun explicitSelect(lookup: LookupImpl, element: LookupElement) {
        UserFactorStorage.applyOnBoth(lookup.project, UserFactorDescriptions.COMPLETION_FINISH_TYPE) { updater ->
            updater.fireExplicitCompletionPerformed()
        }

        val prefixLength = lookup.getPrefixLength(element)
        UserFactorStorage.applyOnBoth(lookup.project, UserFactorDescriptions.PREFIX_LENGTH_ON_COMPLETION) { updater ->
            updater.fireCompletionPerformed(prefixLength)
        }

        val itemPosition = lookup.selectedIndex
        if (itemPosition != -1) {
            UserFactorStorage.applyOnBoth(lookup.project, UserFactorDescriptions.SELECTED_ITEM_POSITION) { updater ->
                updater.fireCompletionPerformed(itemPosition)
            }
        }

        if (prefixLength > 1) {
            val pattern = lookup.itemPattern(element)
            val isMnemonicsUsed = !element.lookupString.startsWith(pattern)
            UserFactorStorage.applyOnBoth(lookup.project, UserFactorDescriptions.MNEMONICS_USAGE) { updater ->
                updater.fireCompletionFinished(isMnemonicsUsed)
            }

            val storage = MutableLookupStorage.get(lookup)?.getItemStorage(element.idString())
            val type = storage?.getLastUsedFactors()?.get("prefix_matching_type") as? PrefixMatchingType
            if (type != null) {
                UserFactorStorage.applyOnBoth(lookup.project, UserFactorDescriptions.PREFIX_MATCHING_TYPE) { updater ->
                    updater.fireCompletionPerformed(type)
                }
            }
        }
    }
}
