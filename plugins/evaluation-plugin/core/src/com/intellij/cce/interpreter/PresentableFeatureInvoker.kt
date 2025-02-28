package com.intellij.cce.interpreter

import com.intellij.cce.core.Session
import com.intellij.cce.core.TokenProperties
import com.intellij.cce.evaluation.data.Binding
import com.intellij.cce.evaluation.data.EvalDataDescription
import com.intellij.cce.report.CardLayout

/**
 * Feature invoker that was designed to be used directly with [com.intellij.cce.report.CardReportGenerator].
 * The invocation result is easily presentable as a card in an evaluation report.
 */
interface PresentableFeatureInvoker : BindingFeatureInvoker {
  override fun invoke(properties: TokenProperties): PresentableEvalData

  override fun callFeature(expectedText: String, offset: Int, properties: TokenProperties): Session =
    invoke(properties).session(expectedText, offset, properties)
}

class PresentableEvalData(
  val name: Binding<EvalDataDescription<*, String>>,
  val description: Binding<EvalDataDescription<*, String>>,
  val attachments: List<Binding<EvalDataDescription<*, *>>> = emptyList(),
) : BoundEvalData {
  override val allBindings: List<Binding<EvalDataDescription<*, *>>> = attachments + listOf(description, name)

  val layout: CardLayout = CardLayout(
    name.bindable.data,
    description.bindable.data,
    allBindings.mapNotNull { it.bindable.presenter },
  )
}