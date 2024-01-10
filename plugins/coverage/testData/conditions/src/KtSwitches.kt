class KtSwitches {
  fun singleBranchSwitch1(x: Int) {
    when (x) {
      1 -> println("Case 1")
      2 -> println("Case 2")
    }
  }

  fun singleBranchSwitch2(x: Int) {
    when (x) {
      1 -> println("Case 1")
      2 -> println("Case 2")
    }
  }

  fun defaultBranchSwitch(x: Int) {
    when (x) {
      1 -> println("Case 1")
      2 -> println("Case 2")
      else -> println("Default")
    }
  }

  fun fullyCoveredSwitch(x: Int) {
    when (x) {
      1 -> println("Case 1")
      2 -> println("Case 2")
    }
  }

  fun fullyCoveredSwitchWithDefault(x: Int) {
    when (x) {
      1 -> println("Case 1")
      2 -> println("Case 2")
      else -> println("Default")
    }
  }

  fun fullyCoveredSwitchWithoutDefault(x: Int) {
    when (x) {
      1 -> println("Case 1")
      2 -> println("Case 2")
      else -> println("Default")
    }
  }

  fun fullyCoveredSwitchWithImplicitDefault(x: Int) {
    when (x) {
      1 -> println("Case 1")
      2 -> println("Case 2")
      3 -> println("Case 3")
    }
  }

  fun stringSwitch(s: String?) {
    when (s) {
      "A" -> println("Case A")
      "B" -> println("Case B")
      "C" -> println("Case C")
      "D" -> println("Case D")
      "E" -> println("Case E")
      "F" -> println("Case F")
      "G" -> println("Case G")
    }
  }

  fun switchWithOnlyTwoBranchesTransformsIntoIf(s: String?) {
    when (s) { // TODO this is transformed into 2 branches
      "A" -> println("Case 1")
      "B" -> println("Case 2")
    }
  }

  fun fullStringSwitch(s: String?) {
    when (s) {
      "A" -> println("Case A")
      "B" -> println("Case B")
      "C" -> println("Case C")
      else -> println("Default")
    }
  }

  fun stringSwitchSameHashCode(s: String?) {
    when (s) { // TODO here extra branch is generated
      "Aa" -> println("Case A")
      "BB" -> println("Case B")
      "C" -> println("Case C")
      "D" -> println("Case D")
      else -> println("Default")
    }
  }
}
