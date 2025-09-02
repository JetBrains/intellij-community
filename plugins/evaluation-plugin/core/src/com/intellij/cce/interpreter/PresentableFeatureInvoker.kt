package com.intellij.cce.interpreter

import com.intellij.cce.core.TokenProperties
import com.intellij.cce.evaluation.data.Bindable
import com.intellij.cce.evaluation.data.Binding
import com.intellij.cce.evaluation.data.EvalDataDescription
import com.intellij.cce.report.CardLayout

/**
 * Feature invoker that was designed to be used directly with [com.intellij.cce.report.CardReportGenerator].
 * The invocation result is easily presentable as a card in an evaluation report.
 */
interface PresentableFeatureInvoker : BindingFeatureInvoker {
  // TODO suspend to get rid of runBlockingCancellable everywhere
  override fun invoke(properties: TokenProperties): PresentableEvalData
}

data class PresentableEvalData(
  val name: Binding<EvalDataDescription<*, String>>,
  val description: Binding<EvalDataDescription<*, String>>,
  val data: List<Binding<EvalDataDescription<*, *>>> = emptyList(),
) : BoundEvalData {
  override val allBindings: List<Binding<EvalDataDescription<*, *>>> = listOf(name, description) + data

  val layout: CardLayout = CardLayout(
    name.bindable.data,
    description.bindable.data,
    allBindings.mapNotNull { it.bindable.presenter },
  )

  interface Augmenter {
    suspend fun augment(f: suspend () -> PresentableEvalData): PresentableEvalData
    infix fun <T, B : Bindable<T>> B.bind(value: T): Binding<B> = Binding.create(this, value)
  }

  companion object {
    suspend fun augment(list: List<Augmenter>, f: suspend () -> PresentableEvalData): PresentableEvalData {
      if (list.isEmpty()) {
        return f()
      }
      else {
        return list.first().augment { augment(list.drop(1), f) }
      }
    }
  }
}