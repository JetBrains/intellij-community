import com.example.dumblib.DumbLib

fun baz() {
    Dumb<caret:doc>Lib.fo<caret:doc>o()
    B<caret:doc>ar
    b<caret:doc>ar()
}

/**
 * Common doc for object Bar
 */
expect object Bar

/**
 * Common doc fun bar
 */
expect fun bar()
