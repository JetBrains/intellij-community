// FIR_COMPARISON
// FIR_IDENTICAL
import java.nio.charset.Charset

fun charset(charsetName: String): Charset = Charset.forName(charsetName)

fun test() = chars<caret>

// ELEMENT: charset
// TAIL_TEXT: "(charsetName: String) (kotlin.text)"