package org.jetbrains.deft.impl

import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.impl.fields.*
import org.jetbrains.deft.obj.impl.ObjBuilderImplWrapper
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

abstract class ObjType<T : Obj, B : ObjBuilder<T>>(private val _module: ObjModule, val id: Int, val base: ObjType<*, *>? = null) : Obj {
    var open: Boolean = false
    var abstract: Boolean = false
    var sealed: Boolean = false

    val inheritanceAllowed get() = open || sealed || abstract

    val module: ObjModule
        get() = _module.require()

    val fullId get() = Id<T, B>(module.id, id)

    data class Id<T : Obj, B : ObjBuilder<T>>(val module: ObjModule.Id, val id: Int)

    val structure: TStructure<T, B> = TStructure(this, base?.structure)

    val ival: Class<T> get() = javaClass.enclosingClass as Class<T>
    val ivar: Class<B> get() = ival.classes.single { it.simpleName == "Builder" } as Class<B>

    override val factory: ObjType<*, *> get() = TODO("meta type")
    override val parent: Obj? get() = null

    open val packageName: String
        get() = ival.packageName

    override val name by lazy {
        if (ival.enclosingClass == null) ival.simpleName else {
            var topLevelClass: Class<*> = ival
            val outerNames = mutableListOf<String>()
            do {
                outerNames.add(topLevelClass.simpleName)
                topLevelClass = topLevelClass.enclosingClass ?: break
            } while (true)
            outerNames.reversed().joinToString(".")
        }
    }

    @ObjModule.InitApi
    fun link(linker: ObjModules) {
        structure.link(linker)
    }

    protected open fun loadBuilderFactory(): () -> B {
        return module._loadBuilderFactory(this)
    }

    private val _builder: () -> B by lazy {
        module.require()
        loadBuilderFactory()
    }

    fun builder(): B = _builder()

    inline fun builder(init: B.() -> Unit): B {
        val builder = builder()
        builder.init()
        return builder
    }

    // used only for loading objects
    @Suppress("UNCHECKED_CAST")
    fun _newInstance(): T = (builder() as ObjBuilderImplWrapper<T>).unsafeResultInstance

    operator fun invoke(): B = builder()

    inline operator fun invoke(init: B.() -> Unit): T {
        val builder = builder()
        builder.init()
        return builder.build()
    }

    override fun <V> getValue(field: Field<*, V>): V = TODO()

    override fun toString(): String = name
}