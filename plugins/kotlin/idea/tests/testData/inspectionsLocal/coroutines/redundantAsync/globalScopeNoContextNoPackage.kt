// WITH_STDLIB

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async

suspend fun test() {
    GlobalScope.<caret>async() { 42 }.await()
}