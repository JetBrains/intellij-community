package storage.codegen.patcher

import org.jetbrains.deft.annotations.*
import org.jetbrains.deft.annotations.Enum
import org.jetbrains.deft.intellijWs.IjFile
import kotlin.reflect.KClass

class KtAnnotations {
    val list = mutableListOf<KtAnnotation>()
    val byName by lazy {
        list.associateBy { it.name.text }
    }

    override fun toString(): String = list.joinToString()

    val flags by lazy {
        val result = Flags()
        list.forEach {
            when (it.name.text) {
                Content::class.java.simpleName -> result.content = true
                Open::class.java.simpleName -> result.open = true
                Abstract::class.java.simpleName -> result.abstract = true
                Enum::class.java.simpleName -> result.sealed = true
                RelationDSL::class.java.simpleName -> result.relation = true
            }
        }
        result
    }

    operator fun get(name: String): List<String>? =
        byName[name]?.args?.map { it.text.removeSurrounding("\"") }

    operator fun get(c: Class<*>): List<String>? = get(c.simpleName)

    data class Flags(
        var content: Boolean = false,
        var open: Boolean = false,
        var abstract: Boolean = false,
        var sealed: Boolean = false,
        var relation: Boolean = false,
    )
}

operator fun KtAnnotations?.contains(kClass: KClass<*>): Boolean {
    return this != null && kClass.simpleName in byName
}