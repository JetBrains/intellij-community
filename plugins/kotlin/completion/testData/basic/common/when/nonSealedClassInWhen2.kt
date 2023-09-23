// IGNORE_K1
import java.util.Date

class BeforeArrayAfterDate

fun test(a: Any) {
    when (a) {
        <caret>
    }
}

// WITH_ORDER
// EXIST: is Date
// EXIST: is BeforeArrayAfterDate
// EXIST: is Array
