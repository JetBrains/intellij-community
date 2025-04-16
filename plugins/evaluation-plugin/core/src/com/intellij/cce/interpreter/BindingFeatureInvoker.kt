package com.intellij.cce.interpreter

import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.core.Suggestion
import com.intellij.cce.core.SuggestionSource
import com.intellij.cce.core.TokenProperties
import com.intellij.cce.evaluable.AIA_PROBLEMS
import com.intellij.cce.evaluation.data.Binding
import com.intellij.cce.evaluation.data.Bindable
import com.intellij.cce.evaluation.data.EvalDataDescription
import com.intellij.cce.evaluation.data.DataProps

/**
 * Feature invoker that add an abstraction layer over evaluation data storage format.
 */
interface BindingFeatureInvoker : FeatureInvoker {
  fun invoke(properties: TokenProperties): BoundEvalData

  override fun callFeature(expectedText: String, offset: Int, properties: TokenProperties, sessionId: String): Session =
    invoke(properties).session(expectedText, offset, properties, sessionId)

  override fun comparator(generated: String, expected: String): Boolean = true

  infix fun <T, B : Bindable<T>> B.bind(value: T): Binding<B> = Binding.create(this, value)
}

interface BoundEvalData {
  val allBindings: List<Binding<EvalDataDescription<*, *>>>

  fun session(expectedText: String, offset: Int, tokenProperties: TokenProperties, sessionId: String): Session {
    val session = Session(offset, expectedText, expectedText.length, tokenProperties, sessionId)

    var lookup = Lookup(
      "",
      0,
      listOf(),
      0,
      null,
      selectedPosition = -1,
      false,
      additionalInfo = mapOf(),
    )

    for (bind in allBindings) {
      lookup = bind.dump(lookup)
    }

    val props = DataProps(
      null, // TODO
      null, // TODO
      session.copy().also {
        it.addLookup(lookup)
      },
      lookup
    )

    val problems = allBindings.map { it.bindable }.flatMap { desc ->
      desc.problemIndices(props).map { desc.data.valueId(it) }
    }
    lookup = lookup.copy(additionalInfo = lookup.additionalInfo + mapOf(AIA_PROBLEMS to problems.joinToString("\n")))

    // we add artificial suggestion to be able to set `isRelevant` for precision and recall metrics
    if (lookup.suggestions.isEmpty()) {
      lookup = lookup.copy(suggestions = listOf(
        Suggestion(expectedText, expectedText, SuggestionSource.INTELLIJ)
      ))
    }

    // we have to calculate position after everything else has been added to the lookup since a position relies on metric calculation
    val selectedPosition = if (problems.isNotEmpty()) -1 else 0

    lookup = lookup.copy(
      selectedPosition = selectedPosition,
      suggestions = lookup.suggestions.mapIndexed { index, suggestion ->
        if (index == selectedPosition) suggestion.copy(isRelevant = true) else suggestion
      }
    )

    return session.also {
      it.addLookup(lookup)
    }
  }

  companion object {
    operator fun invoke(vararg bindings: Binding<EvalDataDescription<*, *>>): BoundEvalData = object : BoundEvalData {
      override val allBindings: List<Binding<EvalDataDescription<*, *>>> = bindings.toList()
    }
  }
}