// Suppress RETURN_VALUE_NOT_USED in some versions
@file:Suppress("warnings")

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import myInterop.*

@OptIn(ExperimentalForeignApi::class)
fun main() {
    helloFromC()?.toKString()
}
