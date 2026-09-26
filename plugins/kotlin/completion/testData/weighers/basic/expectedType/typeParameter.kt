// FIR_COMPARISON

fun xxx1(): Int = 1
fun xxx2(): String = ""

fun <T: CharSequence> test(): T {
  return xxx<caret>
}

// ORDER: xxx1
// ORDER: xxx2
