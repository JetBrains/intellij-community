import com.example.dumblib.DumbLib

fun linuxFoo() {
    Dumb<caret:doc>Lib.fo<caret:doc>o()
    B<caret:doc>ar
    b<caret:doc>ar()
}

/**
 * Linux doc for object Bar
 */
actual object Bar

actual fun bar()
