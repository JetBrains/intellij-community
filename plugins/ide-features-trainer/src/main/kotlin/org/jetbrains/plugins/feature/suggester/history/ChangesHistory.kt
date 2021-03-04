package org.jetbrains.plugins.feature.suggester.history

import org.jetbrains.plugins.feature.suggester.actions.Action
import java.util.LinkedList

open class ChangesHistory<EventType>(private val maxHistorySize: Int) {

    private val history: MutableList<EventType> = LinkedList()

    val size: Int
        get() = history.size

    fun add(event: EventType) {
        history.add(event)
        checkOverflow()
    }

    fun lastOrNull(): EventType? {
        return history.lastOrNull()
    }

    /**
     * Returns event by the index from newest to oldest
     */
    fun get(index: Int): EventType {
        return history[history.size - index - 1]
    }

    fun contains(action: EventType): Boolean {
        return history.contains(action)
    }

    fun asIterable(): Iterable<EventType> {
        return history.asIterable()
    }

    fun clear() {
        history.clear()
    }

    private fun checkOverflow() {
        if (history.size > maxHistorySize) {
            history.removeAt(0)
        }
    }
}

class UserActionsHistory(maxCacheSize: Int) : ChangesHistory<Action>(maxCacheSize)
