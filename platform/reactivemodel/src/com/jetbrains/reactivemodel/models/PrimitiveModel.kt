package com.jetbrains.reactivemodel.models

import com.github.krukow.clj_lang.IPersistentMap
import com.github.krukow.clj_lang.PersistentHashMap
import com.jetbrains.reactivemodel.Diff
import com.jetbrains.reactivemodel.DiffVisitor
import com.jetbrains.reactivemodel.Model
import com.jetbrains.reactivemodel.ModelVisitor
import com.jetbrains.reactivemodel.util.emptyMeta

public data class PrimitiveModel<T: Any>(public val value: T, val metadata: IPersistentMap<String, *> = emptyMeta()): Model {
    override val meta: IPersistentMap<String, *>
        get() = metadata;

    override fun <T> acceptVisitor(visitor: ModelVisitor<T>): T = visitor.visitPrimitiveModel(this)

    override fun patch(diff: Diff<Model>): Model {
        if (diff !is PrimitiveDiff) {
            throw AssertionError()
        }
        return PrimitiveModel<T>(diff.newValue as T)
    }

    override fun diffImpl(other: Model): Diff<Model>? {
        if (other !is PrimitiveModel<*>) {
            throw AssertionError()
        }
        return PrimitiveDiff(other.value)
    }
}

public data class PrimitiveDiff(val newValue: Any): Diff<PrimitiveModel<Any>> {
    override fun <T> acceptVisitor(visitor: DiffVisitor<T>): T = visitor.visitPrimitiveDiff(this)
}