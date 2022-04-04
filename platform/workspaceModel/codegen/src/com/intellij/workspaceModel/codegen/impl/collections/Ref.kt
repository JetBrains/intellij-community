package org.jetbrains.deft.collections

import com.intellij.workspaceModel.codegen.impl.ObjGraph
import kotlinx.io.core.Input
import kotlinx.io.core.Output
import org.jetbrains.deft.*
import org.jetbrains.deft.impl.ObjImpl
import org.jetbrains.deft.impl._implObj
import org.jetbrains.deft.obj.impl.ObjImplWrapper

interface WithRefs {
    fun ensureInGraph(value: ObjGraph?)
}

/**
 * Cached reference to object.
 *
 * [id] is source of truth (except uncommitted new objects).
 * [cached] is used to perform object loading only once.
 *
 * Able to hold reference to uncommitted new object.
 * In this case [id] is [ObjId.newIdHolder] and [cached] is used as primary source.
 */
open class Ref<T : Obj>(
    id: ObjId<T>,
    cached: ObjImplWrapper? = null
) : WithRefs {
    var id = id
        private set
    var cached = cached
        private set

    val isNull: Boolean
        get() = id == ObjId.nothing

    override fun ensureInGraph(value: ObjGraph?) {
        cached?.impl?.ensureInGraph(value)
    }

    fun get(graph: ObjGraph?): T? {
        val cached = cached
        return when {
            id == ObjId.nothing -> null
            cached != null && cached.impl._id == id && cached.impl.graph == graph -> cached as T
            else -> {
                val t = graph!!.getOrLoad(id) as ObjImplWrapper
                this.cached = t
                t as T
            }
        }
    }

    fun freeze() {
        cached?.impl?.freeze()
    }

    private val equalityKey: Any
        get() =
            when {
                id.isNewIdHolder() -> {
                    val cached = cached!!
                    val actualId = cached.impl._id
                    when {
                        actualId.isNewIdHolder() -> cached
                        else -> actualId
                    }
                }
                else -> id
            }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Ref<*>) return false

        return equalityKey == other.equalityKey
    }

    override fun hashCode(): Int {
        return equalityKey.hashCode()
    }

    override fun toString(): String {
        return if (id.isNewIdHolder()) "<uncommitted new> $cached"
        else id.toString()
    }

    companion object {
        val root = Ref(ObjId(1))
    }
}

fun <T : Obj> Ref(obj: T): Ref<T> {
    obj as ObjImpl
    return Ref(obj._id as ObjId<T>, obj)
}

class RefView<T : Obj>(val src: ObjImpl?, val onLink: OnLink<Any?, T>? = null) : ValueView<T, Ref<T>> {
    override fun aToB(it: T): Ref<T> {
        val objImpl = it._implObj
        src?._addRef(objImpl)
        onLink?.add(src, it)
        return Ref(objImpl._id as ObjId<T>, objImpl)
    }

    override fun bToA(it: Ref<T>): T = it.get(src?.graph) as T

    override fun remove(it: Ref<T>) {
        onLink?.remove(src, it.get(src?.graph!!) as T)
    }
}