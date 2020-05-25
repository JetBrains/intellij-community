package org.jetbrains.plugins.feature.suggester.cache

import java.util.*

open class ChangedCache<EventType>(val maxCacheSize: Int) {

    private val cache: MutableList<EventType> = LinkedList<EventType>()

    val last: EventType = cache.last()

    fun add(event: EventType) {
        cache.add(event)
        checkOverflow()
    }

    /**
     * Returns event by the index from newest to oldest
     */
    fun get(index: Int): EventType {
        return cache[cache.size - index - 1]
    }

    fun contains(action: EventType): Boolean {
        return cache.contains(action)
    }

    private fun checkOverflow() {
        if (cache.size > maxCacheSize) {
            cache.removeAt(0)
        }
    }
}