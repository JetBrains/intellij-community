// FIR_IDENTICAL
// FIR_COMPARISON
import java.util.HashMap

fun foo() {
    val v = HashMap<<caret>
}

// EXIST: String
// EXIST: kotlin
// ABSENT: defaultBufferSize
// ABSENT: readLine
