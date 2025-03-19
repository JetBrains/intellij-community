// PRIORITY: LOW
// AFTER-WARNING: The value 'if (n > 0)<br>        "> 0"<br>    else<br>        "<= 0"' assigned to 'val x: String defined in foo' is never used
// AFTER-WARNING: Variable 'x' is assigned but never accessed
fun foo(n: Int) {
    <caret>val x =
        if (n > 0)
            "> 0"
        else
            "<= 0"
}