package com.intellij.cce.evaluation

class StopEvaluationException(override val message: String? = null, override val cause: Throwable? = null) : Throwable()