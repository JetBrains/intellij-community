package com.intellij.cce.evaluation.data

/**
 * Represents a data entity produced during evaluation processes.
 *
 * @property name The unique name of the evaluation data.
 * @property placement The placement associated with the evaluation data, responsible for its storage.
 */
data class EvalData<T>(
  val name: String,
  val placement: DataPlacement<*, T>,
) {
  fun valueId(valueIndex: Int): String = "$name:$valueIndex"

  companion object {
    fun checkUniqueness(data: List<EvalData<*>>) {
      val duplicateNames = data.groupingBy { it.name }.eachCount().filter { it.value > 1 }
      check(duplicateNames.isEmpty()) {
        "There are duplicate names: ${duplicateNames.keys.joinToString(", ")}"
      }

      val duplicatePlacements = data.groupingBy { it.placement }.eachCount().filter { it.value > 1 }
      check(duplicatePlacements.isEmpty()) {
        "There are duplicate placements: ${duplicatePlacements.keys.joinToString(", ")}"
      }

      // TODO check that all property names are distinct
    }
  }
}