package org.jetbrains.deft.collections

import kotlinx.io.core.Input
import kotlinx.io.core.Output
import org.jetbrains.deft.Obj
import org.jetbrains.deft.OnLink
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

@Suppress("UNCHECKED_CAST")
fun <E> TList<E>.listView(obj: ObjImpl?): ValueView<List<E>, Any?> {
    val elementView = elementType.wrapperView(obj)
    return object : ValueView<List<E>, Any?> {
        override fun aToB(it: List<E>) = ListView(obj, elementView.reversed, it as MutableList<E>)
        override fun bToA(it: Any?) = ListView(obj, elementView, it as MutableList<Any?>)
        override fun remove(it: Any?) = (it as MutableList<Any?>).clear()
    }
}

@Suppress("UNCHECKED_CAST")
fun <K, V> TMap<K, V>.mapView(obj: ObjImpl?): ValueView<Map<K, V>, Any?> {
    val kv = keyType.wrapperView(obj)
    val vv = valueType.wrapperView(obj)
    return object : ValueView<Map<K, V>, Any?> {
        override fun aToB(it: Map<K, V>) = MapView(obj, kv, vv, it as MutableMap<Any?, Any?>)
        override fun bToA(it: Any?) = MapView(obj, kv.reversed, vv.reversed, it as MutableMap<K, V>) as Map<K, V>
        override fun remove(it: Any?) = (it as MutableMap<*, *>).clear()
    }
}

@Suppress("UNCHECKED_CAST")
fun <V> TOptional<V>.optionalView(obj: ObjImpl?): ValueView<V?, Any?> {
    return when (val tv = type.wrapperView(obj)) {
        ValueView.id -> ValueView.id as ValueView<V?, Any?>
        else -> object : ValueView<V?, Any?> {
            override fun aToB(it: V?): Any? = if (it == null) null else tv.aToB(it)
            override fun bToA(it: Any?): V? = if (it == null) null else tv.bToA(it)
            override fun remove(it: Any?) {
                if (it != null) tv.remove((it as V))
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun <V : Obj> TRef<V>.refView(obj: ObjImpl?) = RefView(obj, onLink as OnLink<Any?, V>?) as ValueView<V, Any?>

@Suppress("UNCHECKED_CAST")
fun <E> TList<E>.newList(obj: ObjImpl?): ListView<Any?, Any?> =
    listView(obj).bToA(mutableListOf<Any?>()) as ListView<Any?, Any?>

fun <K, V> TMap<K, V>.newMap(owner: ObjImpl?) = wrapperView(owner).aToB(mutableMapOf())

interface ValueView<A, B> {
    fun aToB(it: A): B // set
    fun bToA(it: B): A // get

    fun remove(it: B) = Unit
    fun removeAll(src: Iterable<B>): Unit =
        src.forEach { remove(it) }

    open val reversed: ValueView<B, A> get() = ReversedValueView(this)

    companion object {
        val id = object : ValueView<Any, Any> {
            override fun aToB(it: Any): Any = it
            override fun bToA(it: Any): Any = it
            override val reversed: ValueView<Any, Any> get() = this
        }

        fun <T> id(): ValueView<T, T> = id as ValueView<T, T>
    }
}

class ReversedValueView<A, B>(val src: ValueView<A, B>) : ValueView<B, A> {
    override fun aToB(it: B): A = src.bToA(it)
    override fun bToA(it: A): B = src.aToB(it)
    override val reversed: ValueView<A, B> get() = src
}

class IteratorView<A, B>(val obj: ObjImpl?, val f: ValueView<A, B>, val src: MutableIterator<B>) : MutableIterator<A> {
    var item: B? = null
    override fun hasNext(): Boolean = src.hasNext()
    override fun next(): A {
        val next = src.next()
        item = next
        return f.bToA(next)
    }

    override fun remove() {
        obj?._markChanged()
        f.remove(item!!)
        src.remove()
    }
}

class ListIteratorView<A, B>(
    val obj: ObjImpl?,
    val f: ValueView<A, B>,
    val src: MutableListIterator<B>
) : MutableListIterator<A> {
    var item: B? = null
    override fun hasNext(): Boolean = src.hasNext()
    override fun next(): A {
        val next = src.next()
        item = next
        return f.bToA(next)
    }

    override fun remove() {
        obj?._markChanged()
        f.remove(item!!)
        src.remove()
    }

    override fun hasPrevious(): Boolean = src.hasPrevious()
    override fun nextIndex(): Int = src.nextIndex()
    override fun previous(): A = f.bToA(src.previous())
    override fun previousIndex(): Int = src.previousIndex()
    override fun add(element: A) {
        obj?._markChanged()
        src.add(f.aToB(element))
    }

    override fun set(element: A) {
        obj?._markChanged()
        f.remove(item!!)
        src.set(f.aToB(element))
    }
}

open class CollectionView<A, B>(val obj: ObjImpl?, val f: ValueView<A, B>, val src: MutableCollection<B>) :
    MutableCollection<A> {
    override val size: Int
        get() = src.size

    override fun contains(element: A): Boolean = src.contains(f.aToB(element))
    override fun containsAll(elements: Collection<A>): Boolean = src.containsAll(elements.map { f.aToB(it) })
    override fun isEmpty(): Boolean = src.isEmpty()
    override fun iterator(): MutableIterator<A> = IteratorView(obj, f, src.iterator())
    override fun add(element: A): Boolean {
        val add = src.add(f.aToB(element))
        if (add) obj?._markChanged()
        return add
    }

    override fun addAll(elements: Collection<A>): Boolean {
        var hasSomething = false
        elements.forEach { if (add(it)) hasSomething = true }
        if (hasSomething) obj?._markChanged()
        return hasSomething
    }

    override fun clear() {
        obj?._markChanged()
        f.removeAll(src)
        src.clear()
    }

    override fun remove(element: A): Boolean {
        obj?._markChanged()
        return src.remove(f.aToB(element))
    }

    override fun removeAll(elements: Collection<A>): Boolean {
        var hasSomething = false
        elements.forEach { if (remove(it)) hasSomething = true }
        if (hasSomething) obj?._markChanged()
        return hasSomething
    }

    override fun retainAll(elements: Collection<A>): Boolean {
        val retainAll = src.retainAll(elements.map { f.aToB(it) })
        if (retainAll) obj?._markChanged()
        return retainAll
    }
}

class SetView<A, B>(obj: ObjImpl?, f: ValueView<A, B>, src: MutableSet<B>) : CollectionView<A, B>(obj, f, src),
    MutableSet<A>

class ListView<A, B>(
    obj: ObjImpl?,
    f: ValueView<A, B>,
    src: MutableList<B>
) : CollectionView<A, B>(obj, f, src),
    MutableList<A> {

    val srcList get() = src as MutableList<B>
    override fun get(index: Int): A = f.bToA(srcList[index])
    override fun indexOf(element: A): Int = srcList.indexOf(f.aToB(element))
    override fun lastIndexOf(element: A): Int = srcList.lastIndexOf(f.aToB(element))
    override fun add(index: Int, element: A) {
        obj?._markChanged()
        srcList.add(index, f.aToB(element))
    }

    override fun addAll(index: Int, elements: Collection<A>): Boolean {
        obj?._markChanged()
        return srcList.addAll(index, elements.map { f.aToB(it) })
    }

    override fun listIterator(): MutableListIterator<A> =
        ListIteratorView(obj, f, srcList.listIterator())

    override fun listIterator(index: Int): MutableListIterator<A> =
        ListIteratorView(obj, f, srcList.listIterator(index))

    override fun removeAt(index: Int): A {
        obj?._markChanged()
        val v = srcList.removeAt(index)
        f.remove(v)
        return f.bToA(v)
    }

    override fun set(index: Int, element: A): A {
        obj?._markChanged()
        f.remove(srcList[index])
        return f.bToA(srcList.set(index, f.aToB(element)))
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<A> =
        ListView(obj, f, srcList.subList(fromIndex, toIndex))
}

fun <V> CollectionView<*, V>?.freezeCheck(field: Field<*, *>, f: (V) -> Boolean): Boolean {
    if (this == null) return true
    try {
        src.forEach {
            if (!f(it)) throw MissedValue(obj, field, null)
        }
    } catch (e: MissedValue) {
        throw MissedValue(obj, field, e)
    }
    return true
}

inline fun <V> Output.writeListView(
    list: ListView<*, V>?,
    value: (V) -> Unit,
) {
    if (list == null) {
        writeInt(0)
    } else {
        writeInt(list.size)
        list.src.forEach {
            value(it)
        }
    }
}

inline fun <A, B> Input.readListView(
    new: () -> ListView<A, B>,
    value: () -> B,
): ListView<A, B>? {
    val size = readInt()
    if (size == 0) return null
    val result = new()
    repeat(size) { result.src.add(value()) }
    return result
}

class MapEntryView<K1, K2, V1, V2>(
    obj: ObjImpl?,
    val key: ValueView<K1, K2>,
    val value: ValueView<V1, V2>
) : ValueView<MutableMap.MutableEntry<K1, V1>, MutableMap.MutableEntry<K2, V2>> {
    class Entry<K, V>(
        override val key: K,
        override val value: V
    ) : MutableMap.MutableEntry<K, V> {
        override fun setValue(newValue: V): V {
            TODO("Not yet implemented")
        }
    }

    override fun aToB(it: MutableMap.MutableEntry<K1, V1>) = Entry(key.aToB(it.key), value.aToB(it.value))
    override fun bToA(it: MutableMap.MutableEntry<K2, V2>) = Entry(key.bToA(it.key), value.bToA(it.value))
}

class MapView<K1, K2, V1, V2>(
    val obj: ObjImpl?,
    val keyt: ValueView<K1, K2>,
    val valuet: ValueView<V1, V2>,
    val src: MutableMap<K2, V2>
) : MutableMap<K1, V1> {
    override val entries: MutableSet<MutableMap.MutableEntry<K1, V1>>
        get() = SetView(obj, MapEntryView(obj, keyt, valuet), src.entries)
    override val keys: MutableSet<K1> get() = SetView(obj, keyt, src.keys)
    override val size: Int get() = src.size
    override val values: MutableCollection<V1> get() = CollectionView(obj, valuet, src.values)
    override fun containsKey(key: K1): Boolean = src.containsKey(keyt.aToB(key))
    override fun containsValue(value: V1): Boolean = src.containsValue(valuet.aToB(value))
    override fun get(key: K1): V1? = src[keyt.aToB(key)]?.let { valuet.bToA(it) }
    override fun isEmpty(): Boolean = src.isEmpty()

    override fun clear() {
        obj?._markChanged()
        keyt.removeAll(src.keys)
        valuet.removeAll(src.values)
        src.clear()
    }

    override fun put(key: K1, value: V1): V1? {
        obj?._markChanged()
        val old = src.put(keyt.aToB(key), valuet.aToB(value)) ?: return null
        valuet.remove(old)
        return valuet.bToA(old)
    }

    override fun putAll(from: Map<out K1, V1>) {
        obj?._markChanged()
        from.entries.forEach { this[it.key] = it.value }
    }

    override fun remove(key: K1): V1? {
        obj?._markChanged()
        val k = keyt.aToB(key)
        val v = src.remove(k) ?: return null
        keyt.remove(k)
        valuet.remove(v)
        return valuet.bToA(v)
    }
}