package com.intellij.cce.evaluable.conflictResolution

interface ConflictResolver<T> {
  fun resolveConflicts(props: Props): MatchSequence<T>

  class Props(
    val base: String,
    val parent1: String,
    val parent2: String,
    val target: String
  )
}

open class TheirConflictResolver : ConflictResolver<String> {
  override fun resolveConflicts(props: ConflictResolver.Props): MatchSequence<String> {
    val resultText = props.parent2
    val conflicts = calculateAlignment(TextLabel.BASE, props.base, TextLabel.RESULT, resultText)
      .intersectAlignments(calculateAlignment(TextLabel.BASE, props.base, TextLabel.PARENT_1, props.parent1))
      .intersectAlignments(calculateAlignment(TextLabel.BASE, props.base, TextLabel.PARENT_2, props.parent2))
      .intersectAlignments(calculateAlignment(TextLabel.BASE, props.base, TextLabel.TARGET, props.target))
      .complement()

    val nonObviousConflicts = (0 until conflicts.size).filterNot { conflictIndex ->
      conflicts.text(TextLabel.BASE, conflictIndex) == conflicts.text(TextLabel.PARENT_1, conflictIndex) ||
      conflicts.text(TextLabel.BASE, conflictIndex) == conflicts.text(TextLabel.PARENT_2, conflictIndex) ||
      conflicts.text(TextLabel.PARENT_1, conflictIndex) == conflicts.text(TextLabel.PARENT_2, conflictIndex)
    }

    return conflicts.filter(nonObviousConflicts)
  }

  @Suppress("SameParameterValue", "MemberVisibilityCanBePrivate")
  protected fun calculateAlignment(label1: TextLabel, text1: String, label2: TextLabel, text2: String): MatchSequence<String> {
    val splitText1 = LineSplitText(text1)
    val splitText2 = LineSplitText(text2)
    val distances = calculateEditDistance(splitText1, splitText2)
    val (ranges1, ranges2) = retrieveAlignment(distances)
    return MatchSequence(mapOf(label1 to ranges1, label2 to ranges2), mapOf(label1 to splitText1, label2 to splitText2))
  }
}
