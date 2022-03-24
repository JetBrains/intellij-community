// WITH_STDLIB
fun oops(a: String, b: String) : Boolean {
    var i = 0
    var j = 0
    <error descr="[UNRESOLVED_REFERENCE] Unresolved reference: let">let</error> <error descr="[DEBUG] Reference is not resolved to anything, but is not marked unresolved">bLength</error> <error descr="Expecting an element">=</error> <error descr="[DEBUG] Reference is not resolved to anything, but is not marked unresolved">b</error><error descr="Expecting an element">.</error><error descr="[DEBUG] Reference is not resolved to anything, but is not marked unresolved">length</error>

    <error descr="[EXPRESSION_EXPECTED] While is not an expression, and only expressions are allowed here">while (i < b.length) {
        when (val c = b[i++]) {
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
