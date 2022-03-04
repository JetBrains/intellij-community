package org.jetbrains.deft.codegen.ijws

import deft.storage.codegen.lines
import org.jetbrains.deft.intellijWs.*
import storage.codegen.patcher.*

fun DefType.ijWsType(): IjWsType? {
    val annotations = def.annotations

    fun ijWsName(args: List<String>, init: () -> IjWsType): IjWsType {
        val result = init()
        result.ijWsName = args.firstOrNull() ?: name
        return result
    }

    annotations[IjWsEntity::class.java]?.let { return ijWsName(it) { Entity() } }
    annotations[IjWsEnum::class.java]?.let { return ijWsName(it) { Enum() } }
    annotations[IjWsData::class.java]?.let { return ijWsName(it) { Data() } }
    annotations[IjWsSealedData::class.java]?.let { return ijWsName(it) { SealedData() } }
    annotations[IjWsObject::class.java]?.let { return ijWsName(it) { Object() } }

    return null
}

fun DefType.toIjWsCode(): String = declaredIjWsType?._code(this) ?: ""

sealed class IjWsType {
    lateinit var ijWsName: String
    lateinit var ijWsPackage: String

    fun _code(defType: DefType): String = with(defType) { code() }

    abstract fun DefType.code(): String
}