// PROBLEM: none
// WITH_STDLIB
import java.util.*

class MyMap: HashMap<String, String>()

fun test(data: MyMap) {
    val result = data.<caret>map { "${it.key}: ${it.value}" }.joinToString("\n")
}