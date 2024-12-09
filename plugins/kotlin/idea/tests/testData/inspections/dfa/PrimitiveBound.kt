// WITH_STDLIB
class JJJ<T: <warning descr="[FINAL_UPPER_BOUND] Type 'kotlin.Char' is final, so the value of the type parameter is predetermined.">Char</warning>> {
    var s: Array<T> = <error descr="[TYPE_PARAMETER_AS_REIFIED] Cannot use 'T' as reified type parameter. Use a class instead.">arrayOf</error>()
    fun m() {
        s [0] = <error descr="[ARGUMENT_TYPE_MISMATCH] Argument type mismatch: actual type is 'kotlin.Int', but 'T (of class JJJ<T : Char>)' was expected.">2</error>
    }
}