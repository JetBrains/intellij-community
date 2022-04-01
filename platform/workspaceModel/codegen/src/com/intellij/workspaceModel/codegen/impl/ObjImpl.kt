package org.jetbrains.deft.impl

import com.intellij.workspaceModel.codegen.impl.Mutation
import com.intellij.workspaceModel.codegen.impl.ObjGraph
import kotlinx.io.core.Input
import kotlinx.io.core.Output
import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.ObjId
import org.jetbrains.deft.bytes.outputMaxBytes
import org.jetbrains.deft.bytes.readString
import org.jetbrains.deft.bytes.writeString
import org.jetbrains.deft.collections.*
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.obj.impl.ObjImplWrapper
import org.jetbrains.deft.writeId

//import org.jetbrains.deft.writeId

@Suppress("PropertyName")
abstract class ObjImpl : ExtensibleImpl(), Obj, WithRefs, ObjImplWrapper {
    override val impl get() = this

    var mutable: Boolean = true
        private set

    var names: MutableMap<String, ObjImpl>? = null
    fun namesToPut(): MutableMap<String, ObjImpl> {
        if (names == null) names = mutableMapOf()
        return names!!
    }

    inline fun <reified R: Obj?> getOrPutName(name: String, f: () -> R): R {
        val existed = names?.get(name)
        if (existed != null) return existed as R
        else return f()
    }

    val computations = mutableSetOf<Class<*>>()

    inline fun require(computation: Class<*>, f: () -> Unit) {
        if (computations.add(computation)) f()
    }

    protected abstract fun checkInitialized()

    fun freeze() {
        if (!mutable) return
        mutable = false
        try {
            checkInitialized()
        } catch (e: Throwable) {
            // note: linked builders may be still marked as immutable!
            mutable = true
            throw e
        }
    }

    fun unfreeze(loading: Boolean = false) {
        if (!loading) check(this.graph == null || this._mutation != null)
        mutable = true
    }

    protected fun freezeCheck(field: Field<*, *>, condition: Boolean) {
        if (!condition) throw MissedValue(this, field, null)
    }

    protected inline fun freezeCheck(field: Field<*, *>, f: () -> Unit) {
        try {
            f()
        } catch (e: MissedValue) {
            throw MissedValue(this, field, e)
        }
    }

    abstract fun hasNewValue(field: Field<*, *>): Boolean

//    abstract fun isInitialized(field: Field<*, *>): Boolean

    var _id: ObjId<*> = ObjId.newIdHolder
        set(value) {
            check(field.isNewIdHolder()) { "Id already set" }
            if (value.n > 0) {
                field = value
                graph?._register(this)
            }
        }

    var modsCount = 0

    var graph: ObjGraph? = null
        private set

    override fun ensureInGraph(value: ObjGraph?) {
        val oldGraph = graph
        if (oldGraph != value) {
            if (value == null) TODO("detaching object from graph is not supported yet")
            if (oldGraph != null) throw AlienObjectRefError(oldGraph, this, null, value)
            graph = value
            value._register(this)
            (value.owner as? Mutation)?.markChanged(this)
            moveIntoGraph(value)
        }
    }

    protected open fun moveIntoGraph(graph: ObjGraph?) {
        _parent?.ensureInGraph(graph)
        extensionsMoveIntoGraph(graph)
    }

    var _name: String? = ""
        set(value) {
            rename(field, value)
            field = value
        }

    private fun rename(old: String?, new: String?) {
        graph?._rename(this, old, new)
        if (old != new) {
            if (old != null) _parent?.names?.remove(old)
            if (new != null) _parent?.namesToPut()?.put(new, this)
        }
    }

    override val name: String?
        get() = _name

    protected var _parentId: Int = ObjId.nothing.n
    protected var _parent: ObjImpl? = null
    val parentRef: Ref<Obj>
        get() = Ref(ObjId(_parentId), _parent)
    override val parent: Obj?
        get() {
            _parent = _getRef(_parent, _parentId)
            return _parent
        }

    fun setParent(value: Obj?) {
        val valueImpl = (value as ObjImplWrapper?)?.impl
        val oldParent = _parent
        val newParent = _setRef(valueImpl)
        _parent = newParent
        if (newParent != null) _parentId = newParent._id.n
        graph?._setParent(oldParent, newParent, this)

        val name = name
        if (name != null) {
            oldParent?.names?.remove(name)
            newParent?.namesToPut()?.put(name, this)
        }
    }

    internal fun onRefUpdate(old: ObjImpl?, new: ObjImpl?) {
        if (old != new) {
            _setRef(new)
        }
    }

    fun _getRef(cached: ObjImpl?, id: ObjId<*>): ObjImpl? {
        return when {
            cached != null && cached._id == id -> cached
            id == ObjId.nothing -> null
            else -> load(id) as ObjImpl?
        }
    }

    fun _getRef(cached: ObjImpl?, id: Int): ObjImpl? {
        return _getRef(cached, ObjId<Any>(id))
    }

    val _mutation: Mutation?
        get() = this.graph?.owner?.let { it as? Mutation }

    fun _setRef(value: ObjImpl?): ObjImpl? {
        val mutation = _mutation
        mutation?.markChanged(this)

        if (value != null) {
            _addRef(value, mutation)
        }

        return value
    }

    override fun _markChanged() {
        if (!mutable) error("Cannot change immutable $this")
        _mutation?.markChanged(this)
    }

    fun _addRef(target: ObjImpl, mutation: Mutation? = _mutation) {
        val graph = this.graph
        val targetGraph = target.graph
        if (targetGraph != graph) {
            when {
                graph == null -> targetGraph?.owner?.let { it as? Mutation }?.add(this)
                targetGraph == null -> (mutation!! as? Mutation)?.add(target)
                else -> throw AlienObjectRefError(graph, this, target, targetGraph)
            }
        }
    }

    protected fun refsBuilder(existed: Refs?): Refs {
        if (existed != null) return existed
        return Refs(this)
    }

    protected fun childrenBuilder(existed: Children?): Children {
        if (existed != null) return existed
        return Children(this)
    }

    protected fun relsBuilder(existed: Rels?, relFactory: ObjType1<*, ObjBuilder<*>, *>): Rels {
        if (existed != null) return existed
        return Rels(this, relFactory as ObjType1<ObjImpl, ObjBuilder<ObjImpl>, ObjImpl>)
    }

    private fun <T> load(id: ObjId<T>): T {
        val graph = graph ?: error("graph is not defined for $this")
        return graph.getOrLoad(id)
    }

    override fun toString(): String = (if (name?.isEmpty() == true) "$factory" else "$name") + " ($_id $graph)"

    open fun builder(): ObjBuilder<*> = TODO()

    override fun updateRefIds() {
        if (_parent != null) _parentId = _parent!!._id.n
        extensionsUpdateRefIds()
    }

    open fun estimateMaxSize(): Int = name.outputMaxBytes +
            ObjId.bytesCount +
            extensionsEstimateMaxSize()
}