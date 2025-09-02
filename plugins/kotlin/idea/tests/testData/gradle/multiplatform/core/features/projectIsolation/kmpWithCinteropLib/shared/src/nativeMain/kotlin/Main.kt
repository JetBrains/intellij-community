import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import myInterop.*

@OptIn(ExperimentalForeignApi::class)
fun main() {
    helloFromC()?.toKString()
}
