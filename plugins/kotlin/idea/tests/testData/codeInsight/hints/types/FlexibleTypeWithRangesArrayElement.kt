// MODE: all
import java.util.Collections

private fun <T> foo(c: Class<out T>) {
    val a/*<# : |[foo.T:kotlin.fqn.class]T #>*/ = bar(c)[0]
}

fun <T> bar(c: Class<T>): Array<T> {
    throw Error()
}