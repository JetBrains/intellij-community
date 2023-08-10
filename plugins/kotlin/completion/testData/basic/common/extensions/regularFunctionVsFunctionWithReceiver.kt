// FIR_COMPARISON
// FIR_IDENTICAL
package pckg

fun fooBar() {}
fun <T> T.fooBar() {}

class X {
    val test = fooBa<caret>
}

// WITH_ORDER
// EXIST: { "lookupString":"fooBar", "tailText":"() for T in pckg" }
// EXIST: { "lookupString":"fooBar", "tailText":"() (pckg)" }
