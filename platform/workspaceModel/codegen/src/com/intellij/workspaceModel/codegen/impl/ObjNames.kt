package org.jetbrains.deft.impl

import org.jetbrains.deft.ObjId
import org.jetbrains.deft.ObjNames
import org.jetbrains.deft.collections.Ref
import org.jetbrains.deft.rpc.ObjName

@Suppress("UNCHECKED_CAST")
class ObjNamesImpl(names: List<ObjName> = emptyList()) : ObjNames {
    class ObjNode(val ref: Ref<ObjImpl>) {
        val names = mutableMapOf<String, ObjNode>()
    }

    val root: ObjNode = ObjNode(Ref(ObjId(1)))
    val objs: MutableMap<Ref<ObjImpl>, ObjNode> = mutableMapOf()

    init {
        objs[root.ref] = root

        names.forEach {
            val ns = getNs(Ref(ObjId<ObjImpl>(it.id)))
            val parentNs = getNs(Ref(ObjId<ObjImpl>(it.parentId)))

            if (ns != null && parentNs != null) {
                parentNs.names[it.name] = ns
            }
        }
    }

    override fun get(vararg path: String): Ref<*>? {
        return get(root, path)
    }

    override fun get(context: Ref<*>, vararg path: String): Ref<*>? {
        context as Ref<ObjImpl>
        return get(objs[context] ?: return null, path)
    }

    override fun getChildren(context: Ref<*>): Collection<Pair<String, Ref<*>>>? {
        context as Ref<ObjImpl>
        return objs[context]?.names?.entries?.map { it.key to it.value.ref }
    }

    private fun getNs(id: Ref<*>): ObjNode? {
        if (id.isNull) return null
        id as Ref<ObjImpl>
        return objs.getOrPut(id) { ObjNode(id) }
    }

    private fun get(context: ObjNode, path: Array<out String>): Ref<*>? {
        var ns = context
        path.forEach {
            ns = ns.names[it] ?: return null
        }
        return ns.ref
    }

    private val ObjImpl.ref
        get() = Ref(this)

    private val ObjImpl.ns: ObjNode?
        get() = getNs(ref)
    
    private val ObjImpl.parenNs: ObjNode?
        get() = getNs(parentRef)

    fun add(obj: ObjImpl) {
        val name = obj.name ?: return

        val ns = obj.ns
        val parentNs = obj.parenNs

        if (ns != null && parentNs != null) {
            parentNs.names[name] = ns
        }
    }

    fun move(obj: ObjImpl, oldParent: ObjImpl?, newParent: ObjImpl?) {
        if (oldParent == newParent) return

        val ns = obj.ns
        val name = obj.name
        if (name != null && ns != null) {
            if (oldParent != null) oldParent.ns?.names?.remove(name)
            if (newParent != null) newParent.ns?.names?.put(name, ns)
        }
    }

    fun rename(obj: ObjImpl, old: String?, new: String?) {
        if (old == new) return

        val ns = obj.ns
        if (ns != null) {
            val parentNs = obj.parenNs
            if (parentNs != null) {
                if (old != null) parentNs.names.remove(old)
                if (new != null) parentNs.names[new] = ns
            }
        }
    }
}