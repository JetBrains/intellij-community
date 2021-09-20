@file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER")

import kotlinx.coroutines.*
import kotlin.coroutines.*

fun test() {
    runBlocking {
        repeat(1) {
            java.lang.Thread.<warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">sleep</warning>(1)
        }
    }
}

suspend fun test2() {
    val unused1 = run { Thread.<warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">sleep</warning>(2) }

    val unused2 = run (fun() {Thread.<warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">sleep</warning>(3)})
}