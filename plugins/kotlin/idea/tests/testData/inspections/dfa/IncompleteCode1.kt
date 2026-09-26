// WITH_STDLIB
fun oops(a: String, b: String) : Boolean {
    var i = 0
    var j = 0
    <error descr="[UNRESOLVED_REFERENCE]">let</error> <error descr="[DEBUG]">bLength</error> <error descr="Expecting an element">=</error> <error descr="[DEBUG]">b</error><error descr="Expecting an element">.</error><error descr="[DEBUG]">length</error>

    <error descr="[EXPRESSION_EXPECTED]">while (i < b.length) {
        when (val <warning descr="[UNUSED_VARIABLE]">c</warning> = b[i++]) {
            'x' -> {
                while (j < a.length) {
                    if (a[j] == 'y') {
                        break
                    }
                    j++
                }
            }
            else -> return false
        }
    }</error>
    return true
}
