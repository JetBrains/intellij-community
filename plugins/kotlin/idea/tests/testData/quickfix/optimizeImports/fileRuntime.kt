// "Optimize imports" "true"
// WITH_RUNTIME

<caret>import java.io.*
import java.util.*

fun foo(list: ArrayList<String>) {
    list.add("")
}
