// FIR_IDENTICAL
// FIR_COMPARISON
class Outer {
  companion object {
    val value: String = "hello"
  }
}

fun Outer.<caret>

// EXIST: { lookupString: "Companion", itemText: "Companion", tailText: " (Outer)", icon: "org/jetbrains/kotlin/idea/icons/objectKotlin.svg" }
