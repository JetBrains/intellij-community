// WITH_RUNTIME
// IS_APPLICABLE: false
// IS_APPLICABLE_2: false
import java.util.*

fun foo(list: List<String>) {
    val random = Random()
    <caret>for (s in list) {
        if (random.nextBoolean()) continue
        print(s)
    }
}