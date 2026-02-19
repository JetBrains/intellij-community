// WITH_COROUTINES
import kotlinx.coroutines.flow.*

fun f() {
    val outer = <info descr="null">~flow</info> {
        <info descr="null">emit</info>(1)

        val inner = flow {
            emit(99)
            emit(100)
        }

        <info descr="null">emit</info>(2)
        <info descr="null">emitAll</info>(inner)
    }
}