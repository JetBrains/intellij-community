// FIR_IDENTICAL
// FIR_COMPARISON
import java.util.HashMap

fun foo() {
    val v = HashMap<<caret>
}

// EXIST: String
// EXIST_K2: String
// EXIST: kotlin
// EXIST_K2: kotlin.
// ABSENT: defaultBufferSize
// ABSENT: readLine
