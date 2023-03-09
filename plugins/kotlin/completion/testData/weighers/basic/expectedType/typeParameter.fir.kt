// FIR_COMPARISON

fun xxx1(): Int = 1
fun xxx2(): String = ""

fun <T: CharSequence> test(): T {
  return xxx<caret>
}

// ORDER: xxx2
// ORDER: xxx1
