package com.jetbrains.reactivemodel.models

import com.github.krukow.clj_lang.IPersistentMap
import com.jetbrains.reactivemodel.Diff
import com.jetbrains.reactivemodel.Model
import com.jetbrains.reactivemodel.ModelVisitor
import com.jetbrains.reactivemodel.util.emptyMeta

public data class AbsentModel: Model {
    override val meta: IPersistentMap<String, *>
        get() = emptyMeta()

    override fun <T> acceptVisitor(visitor: ModelVisitor<T>): T = visitor.visitAbsentModel(this)

    override fun patch(diff: Diff<Model>): Model = this

    override fun diffImpl(other: Model): Diff<Model>?  = null
}