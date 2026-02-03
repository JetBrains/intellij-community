//region Test configuration
// - hidden: line markers
//endregion
import com.example.dumblib.DumbLib

fun jvmFoo() {
    Dumb<caret:doc>Lib.fo<caret:doc>o(ar<caret:doc>g1 = 0, ar<caret:doc>g2 = 0)
    B<caret:doc>ar
    b<caret:doc>ar()
}

actual object Bar

/**
 * JVM doc fun bar
 */
actual fun bar() {}
