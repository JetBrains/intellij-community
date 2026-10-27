package sameNameDifferentReceivers

import kotlinx.coroutines.runBlocking

fun String.process() {
    runBlocking { }
}

suspend fun Int.process() {
}
