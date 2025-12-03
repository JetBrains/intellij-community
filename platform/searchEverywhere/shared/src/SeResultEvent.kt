// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import org.jetbrains.annotations.ApiStatus

/**
 * Events related to the processing and presentation of search results in the Search Everywhere.
 */
@ApiStatus.Experimental
sealed interface SeResultEvent

/**
 * Event indicating that a new search result has been added.
 */
@ApiStatus.Experimental
class SeResultAddedEvent(val itemData: SeItemData) : SeResultEvent

/**
 * Event indicating that a search result should replace the items with the specified UUIDs.
 */
@ApiStatus.Experimental
class SeResultReplacedEvent(val uuidsToReplace: List<String>, val newItemData: SeItemData) : SeResultEvent

/**
 * Event indicating that the items provider with the specified id has finished.
 */
@ApiStatus.Experimental
class SeResultEndEvent(val providerId: SeProviderId) : SeResultEvent
