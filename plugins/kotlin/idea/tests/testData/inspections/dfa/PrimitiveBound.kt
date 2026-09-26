// WITH_STDLIB
class JJJ<T: <warning descr="[FINAL_UPPER_BOUND]">Char</warning>> {
    var s: Array<T> = <error descr="[TYPE_PARAMETER_AS_REIFIED]">arrayOf</error>()
    fun m() {
        s [0] = <error descr="[ARGUMENT_TYPE_MISMATCH]">2</error>
    }
}