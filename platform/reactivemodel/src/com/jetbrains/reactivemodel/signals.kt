
package com.jetbrains.reactivemodel

import com.intellij.util.containers.MultiMap
import com.jetbrains.reactivemodel.log
import com.jetbrains.reactivemodel.log.catch
import com.jetbrains.reactivemodel.util.Guard
import com.jetbrains.reactivemodel.util.Lifetime
import com.jetbrains.reactivemodel.util.LifetimeDefinition
import java.util.*

public trait Signal<out T> {
    val lifetime : Lifetime
    val value: T
}

public trait MutableSignal<T> : Signal<T> {
    override var value: T
}

public class VariableSignal<T>(val lifetimeDefinition: LifetimeDefinition, val name: String, initial: T) : MutableSignal<T> {
    override val lifetime: Lifetime
        get() = lifetimeDefinition.lifetime

    override var value: T = initial
        get() = $value
        set(newValue) {
            val oldValue = $value
            $value = newValue
            ReactGraph.scheduleUpdate(Change(this, oldValue, newValue))
        }

    override fun toString(): String = name
}

public class Change<out T>(val s: Signal<T>, val oldValue: T, val newValue: T)

object ReactGraph {
    val children: MultiMap<Signal<Any>, Signal<Any>> = MultiMap()
    val parents: MultiMap<Signal<Any>, Signal<Any>> = MultiMap()

    val handlers: MutableMap<Signal<Any>, (List<Any>) -> Any> = HashMap()

    var scheduleOrder: List<Signal<Any>> = listOf()

    val updateQueue: Queue<ArrayList<Change<Any?>>> = ArrayDeque();

    val updatesGuard = Guard()
    val silentUpdateGuard = Guard()

    fun <T, S : Signal<T>> register(s: S, parents: List<Signal<Any>>, handler: (List<Any>) -> T) : S {
        parents.forEach {
            children.putValue(it, s)
        }
        handlers.put(s, handler)
        this.parents.put(s, parents)
        s.lifetime += {
            parents.forEach {
                children.removeValue(it, s)
            }
            this.parents.remove(s)
            handlers.remove(s)
        }
        scheduleOrder = buildScheduleOrder()
        return s
    }

    private fun buildScheduleOrder(): List<Signal<Any>> {
        val visited = hashSetOf<Signal<Any>>()
        val time = arrayListOf<Pair<Int, Signal<Any>>>()
        var curTime = 0
        fun dfs(v: Signal<Any>) : Unit {
            if (visited.add(v)) {
                children.get(v).forEach(::dfs)
                time.add(Pair(curTime++, v))
            }
        }
        children.keySet().forEach {dfs(it)}
        time.sort(compareByDescending { it.first })
        return time.map { it.second }
    }


    fun scheduleUpdate(change: Change<Any?>) {
        if (silentUpdateGuard.locked) return
        if (updateQueue.peek() == null) {
            updateQueue.add(arrayListOf(change))
        } else {
            updateQueue.peek().add(change)
        }
        if (!updatesGuard.locked) {
            fireUpdates()
        }
    }

    fun fireUpdates() {
        while (updateQueue.isNotEmpty()) {
            val changes = updateQueue.poll()
            processChanges(changes)
        }
    }

    private fun processChanges(changes: ArrayList<Change<Any?>>) {
        val changed = HashSet(changes.filter { it.newValue != it.oldValue }.map { it.s })
        updatesGuard.lock {
            scheduleOrder.forEach { signal ->
                if (changed.contains(signal)) {
                    children.get(signal).forEach { child ->
                        val oldValue = child.value
                        val args = parents.get(child).map { parent -> parent.value }.toCollection(ArrayList<Any>())
                        val handler = handlers.get(child)
                        if (handler != null) {
                            val newValue = log.catch { handler(args) }
                            if (newValue != oldValue) {
                                if (child is VariableSignal) {
                                    silentUpdateGuard.lock {
                                        child.value = newValue
                                    }
                                }
                                changed.add(child)
                            }
                        }
                    }
                }
            }
        }
    }
}

inline fun updates(inline t: () -> Unit) {
    ReactGraph.updatesGuard.lock {
        t()
        ReactGraph.fireUpdates()
    }
}

fun <T1, T> reaction(immediate:Boolean = false, name: String = "anonymous", s1 : Signal<T1>, handler: (T1) -> T) : VariableSignal<T> {
    return ReactGraph.register(VariableSignal<T>(Lifetime.create(s1.lifetime), name,
            if (immediate) handler(s1.value) else null as T),
            arrayListOf(s1),
            { handler(it[0] as T1) })
}

fun <T1, T2, T> reaction(immediate: Boolean = false, name: String, s1 : Signal<T1>, s2: Signal<T2>, handler: (T1, T2) -> T) : VariableSignal<T> {
    return ReactGraph.register(VariableSignal<T>(Lifetime.create(s1.lifetime, s2.lifetime), name,
            if (immediate) handler(s1.value, s2.value)
            else null as T),
            arrayListOf(s1, s2),
            { handler(it[0] as T1, it[1] as T2) })
}

fun <T1, T2, T3, T> reaction(immediate: Boolean = false, name: String, s1 : Signal<T1>, s2: Signal<T2>, s3: Signal<T3>, handler: (T1, T2, T3) -> T) : VariableSignal<T> {
    return ReactGraph.register(VariableSignal<T>(
        Lifetime.create(s1.lifetime, s2.lifetime, s3.lifetime),
            name,
            if (immediate) handler(s1.value, s2.value, s3.value) else null as T),
            arrayListOf(s1, s2, s3),
            { handler(it[0] as T1, it[1] as T2, it[2] as T3) })
}

fun <T1, T2, T3, T4, T> reaction(immediate: Boolean = false, name: String, s1 : Signal<T1>, s2: Signal<T2>, s3: Signal<T3>, s4: Signal<T4>, handler: (T1, T2, T3, T4) -> T) : VariableSignal<T> {
    return ReactGraph.register(VariableSignal<T>(Lifetime.create(s1.lifetime, s2.lifetime, s3.lifetime, s4.lifetime),
            name,
            if (immediate) handler(s1.value, s2.value, s3.value, s4.value) else null as T),
            arrayListOf(s1, s2, s3, s4),
            { handler(it[0] as T1, it[1] as T2, it[2] as T3, it[3] as T4) })
}

fun<T> varSignal(lifetime: Lifetime = Lifetime.Eternal, name: String = "anonymous", initial: T) : VariableSignal<T> {
    return VariableSignal<T>(Lifetime.create(lifetime), name, initial)
}

fun main(args: Array<String>) : Unit {
    val s1 = varSignal(initial=0, name="int-var1")
    val s2 = varSignal(initial=1, name="int-var2")

    val sum = reaction(true, "+", s1, s2) { i1, i2->
        i1+i2
    }
    val square = reaction(true, "square", sum) {
        it*it
    }
    val cube = reaction(true, "cube", sum) {
        it*it*it
    }
    val squarePlusCube = reaction(true, "square+cube", square, cube) { sq, cb ->
        sq+cb
    }

    val println = reaction(false, "println", squarePlusCube) {
        println(it)
    }
    updates {
        s1.value = 1
        s2.value = 2
    }
    println.lifetime.terminate()
    s1.value = 3
    //nothing should be printed here
}
