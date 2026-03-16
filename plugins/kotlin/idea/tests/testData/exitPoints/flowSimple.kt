// WITH_COROUTINES
import kotlinx.coroutines.flow.*

fun f() {
    val fl = <info descr="null">~flow</info> {
        <info descr="null">emit</info>(1)
        <info descr="null">emit</info>(2)
        <info descr="null">emitAll</info>(flowOf(3, 4))
    }
}