// WITH_STDLIB
fun oops(a: String, b: String) : Boolean {
    var i = 0
    var j = 0
    // Difference with K1: error messages text is different
    <error descr="[UNRESOLVED_REFERENCE] Unresolved reference 'let'.">let</error> bLength <error descr="Expecting an element">=</error> b<error descr="Expecting an element">.</error>length

    <error descr="[EXPRESSION_EXPECTED] Only expressions are allowed here.">while (i < b.length) {
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
