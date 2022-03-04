package org.jetbrains.deft.collections

class RequiredHashSet<T> : MutableRequiredSet<T> {
    private val isRequired = mutableMapOf<T, Boolean>()

    override fun isRequired(element: T): Boolean = isRequired[element] == true
    override fun isOptional(element: T): Boolean = isRequired[element] == false

    override fun add(element: T): Boolean =
        isRequired.put(element, true) != null

    override fun addAll(elements: Collection<T>): Boolean {
        var changed = false
        elements.forEach {
            if (isRequired.put(it, true) != null) changed = true
        }
        return changed
    }

    override fun clear() {
        isRequired.clear()
    }

    override fun remove(element: T): Boolean {
        val removed = isRequired.remove(element)
        if (removed == true)
            throw RequiredSet.RequiredElementException(this, element)
        return removed != null
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        var changed = false
        elements.forEach {
            if (remove(it)) changed = true
        }
        return changed
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        var changed = false
        elements.forEach {
            when (isRequired[it]) {
                null -> Unit
                true -> RequiredSet.RequiredElementException(this, it)
                false -> {
                    isRequired.remove(it)
                    changed = true
                }
            }
        }
        return changed
    }

    override fun addOptional(element: T): Boolean = isRequired.putIfAbsent(element, false) != null
    override fun addAllOptional(elements: Collection<T>): Boolean {
        var changed = false
        elements.forEach {
            if (addOptional(it)) changed = true
        }
        return changed
    }

    override val size: Int get() = isRequired.size
    override fun contains(element: T): Boolean = element in isRequired
    override fun containsAll(elements: Collection<T>): Boolean = isRequired.keys.containsAll(elements)
    override fun isEmpty(): Boolean = isRequired.isEmpty()
    override fun iterator(): MutableIterator<T> = Iterator()

    inner class Iterator() : MutableIterator<T> {
        val i = isRequired.iterator()
        var item: Any? = null
        var required = false
        override fun hasNext(): Boolean = i.hasNext()
        override fun next(): T {
            val n = i.next()
            required = n.value
            item = n.key
            return n.key
        }

        override fun remove() {
            if (required) throw RequiredSet.RequiredElementException(this@RequiredHashSet, item)
            i.remove()
        }
    }
}