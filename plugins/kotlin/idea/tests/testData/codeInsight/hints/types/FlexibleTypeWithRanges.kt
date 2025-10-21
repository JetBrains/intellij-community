// MODE: all
import java.util.Collections

private fun <T> foo(c: Class<out T>) {
    val a/*<# : |[kotlin.Array:kotlin.fqn.class]Array|<|out |[foo.T:kotlin.fqn.class]T|> #>*/ = bar(c)
}

fun <T> bar(c: Class<T>): Array<T> {
    throw Error()
}