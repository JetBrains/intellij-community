package com.jetbrains.reactivemodel.models

import com.jetbrains.reactivemodel.Diff
import com.jetbrains.reactivemodel.Model

public data class AbsentModel: Model {
    override fun patch(diff: Diff<Model>): Model {
        throw AssertionError()
    }

    override fun diffImpl(other: Model): Diff<Model>? {
        throw AssertionError()
    }
}