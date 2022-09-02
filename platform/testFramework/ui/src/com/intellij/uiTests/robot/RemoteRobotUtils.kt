// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiTests.robot

import com.intellij.remoterobot.stepsProcessing.StepProcessor
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.Color
import com.intellij.remoterobot.utils.color

private const val OPTIONAL_STEP = "Optional step"
internal fun optional(action: () -> Unit) {
  try {
    step(OPTIONAL_STEP) {
      action()
    }
  }
  catch (ignore: Throwable) {
  }
}

class StepPrinter : StepProcessor {

  private var indent = ThreadLocal.withInitial { 0 }

  private fun indents() = buildString {
    repeat(indent.get()) { append("  ") }
  }

  override fun doBeforeStep(stepTitle: String) {
    println(indents() + stepTitle)
    indent.set(indent.get().plus(1))
  }

  override fun doOnSuccess(stepTitle: String) {

  }

  override fun doOnFail(stepTitle: String, e: Throwable) {
    if (stepTitle == OPTIONAL_STEP) {
      println("${indents()}$stepTitle".color(Color.BLUE))
    } else {
      println("${indents()}$stepTitle".color(Color.RED))
    }
  }

  override fun doAfterStep(stepTitle: String) {
    indent.set(indent.get().minus(1))
  }
}