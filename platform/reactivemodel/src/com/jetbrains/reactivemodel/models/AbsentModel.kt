package com.jetbrains.reactivemodel.models

import com.jetbrains.reactivemodel.Diff
import com.jetbrains.reactivemodel.Model
import com.jetbrains.reactivemodel.ModelVisitor

public data class AbsentModel: Model {
    override fun <T> acceptVisitor(visitor: ModelVisitor<T>): T = visitor.visitAbsentModel(this)

    override fun patch(diff: Diff<Model>): Model = this

    override fun diffImpl(other: Model): Diff<Model>?  = null
}