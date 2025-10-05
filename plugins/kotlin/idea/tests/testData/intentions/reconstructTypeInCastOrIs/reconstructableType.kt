// IS_APPLICABLE: true
// PRIORITY: LOW
open class B<T>
class G<T>: B<T>()

fun foo(a: B<String>) = a as <caret>G
