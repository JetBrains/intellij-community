// IGNORE_FIR
// WITH_STDLIB

import java.util.HashMap

fun foo(map : HashMap<String, Int>) {
    for (<caret>entry in map.entries) {

    }
}