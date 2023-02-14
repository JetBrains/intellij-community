// "Optimize imports" "true"
// WITH_STDLIB

<caret>import java.io.*
import java.util.*

fun foo(list: ArrayList<String>) {
    list.add("")
}
