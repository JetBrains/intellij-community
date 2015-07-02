package com.jetbrains.reactivemodel.util

import com.jetbrains.reactivemodel.log
import com.jetbrains.reactivemodel.log.catch

public class LifetimeDefinition internal() {
    public val lifetime : Lifetime = Lifetime()
    public fun terminate() {
        if (!lifetime.isTerminated)
            lifetime.terminate()
    }
}

public class Lifetime internal (eternal : Boolean = false) {

    public companion object {
        public val Eternal : Lifetime = Lifetime(true)
        public fun create(vararg parents : Lifetime) : LifetimeDefinition {
            val res = LifetimeDefinition()
            for (parent in parents) {
                parent.addNested(res)
            }
            return res
        }

        public fun create(parents : List<Lifetime>) : LifetimeDefinition {
            val res = LifetimeDefinition()
            for (parent in parents) {
                parent.addNested(res)
            }
            return res
        }
    }

    public val isEternal : Boolean = eternal
    public var isTerminated : Boolean = false
        private set

    private val actions = arrayListOf<()->Unit>()

    public fun plusAssign(action : () -> Unit) {
        add(action)
    }
    public fun add(action : () -> Unit) {
        if (isTerminated) throw IllegalStateException("Already terminated")
        actions.add (action)
    }

    //short-living lifetimes could explode action termination queue, so we need to drop them after termination
    internal fun addNested(def : LifetimeDefinition) {
        if (def.lifetime.isTerminated) return;

        var action = {def.terminate()}
        add(action)
        def.lifetime.add({actions.remove(action)})
    }

    internal fun terminate() {
        isTerminated = true;
        var actionsCopy = actions//.copyToArray()
//        actions.clear()
        actionsCopy.reverse().forEach {
            catch { it() }
        }
        actions.clear()
    }
}

fun main(args : Array<String>) {
    val def = Lifetime.create(Lifetime.Eternal)

    def.lifetime += {print("World")}
    def.lifetime += {print("Hello")}
    val def2 = Lifetime.create(def.lifetime)
    def2.lifetime += {print("1")}
    def.terminate()
}