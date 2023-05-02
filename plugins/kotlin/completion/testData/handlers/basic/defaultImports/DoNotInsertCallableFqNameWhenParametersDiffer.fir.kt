// FIR_COMPARISON
import java.nio.charset.Charset

fun charset(n: Int): Charset = Charset.forName(charsetName)

fun test() = chars<caret>

// ELEMENT: charset
// TAIL_TEXT: "(charsetName: String) (kotlin.text)"