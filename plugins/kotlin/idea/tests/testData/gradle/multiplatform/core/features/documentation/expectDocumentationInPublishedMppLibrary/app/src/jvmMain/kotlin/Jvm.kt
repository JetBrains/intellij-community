import com.example.dumblib.DumbLib

fun jvmFoo() {
    Dumb<caret:doc>Lib.fo<caret:doc>o()
    B<caret:doc>ar
    b<caret:doc>ar()
}

actual object Bar

/**
 * JVM doc fun bar
 */
actual fun bar()
