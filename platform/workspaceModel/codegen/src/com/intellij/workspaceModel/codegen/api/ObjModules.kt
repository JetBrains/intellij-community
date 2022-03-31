package org.jetbrains.deft.impl

import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjBuilder

class ObjModules {
    /* internal */val byId = mutableMapOf<ObjModule.Id, ObjModule>()

    /* internal */fun typeIndex(id: Int) = id - 1

    operator fun get(id: ObjModule.Id): ObjModule? =
        byId[id]

    fun <T : Obj, B : ObjBuilder<T>> type(id: ObjType.Id<T, B>): ObjType<T, B> = this.get(id)

    @OptIn(ObjModule.InitApi::class)
    /* internal */operator fun <T : Obj, B : ObjBuilder<T>> get(id: ObjType.Id<T, B>): ObjType<T, B> {
        val module = byId[id.module]
            ?: error("Module \"${id.module}\" is not loaded")

        val type = module.byId[typeIndex(id.id)]
            ?: error("Type \"${id.id}\" is not registered in $module")

        return type as ObjType<T, B>
    }

    fun requireByPackageName(packageName: String, classLoader: ClassLoader): ObjModule {
        val last = packageName.substringAfterLast('.').replaceFirstChar { it.titlecaseChar() }
        val cl = classLoader.loadClass("$packageName.$last")
        return cl.declaredFields.find { it.name == "INSTANCE" }!!.get(null) as ObjModule
    }
}