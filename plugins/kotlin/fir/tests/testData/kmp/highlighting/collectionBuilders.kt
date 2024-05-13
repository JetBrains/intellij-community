// PLATFORM: Common
// FILE: common.kt

fun commonThings() {
    val c = listOf(1, 2, 3)
    val d = sequenceOf("a", "b")
    val e = mapOf("1" to 1)
}

// PLATFORM: Jvm
// FILE: jvm.kt

fun jvmThings() {
    val c = listOf(1, 2, 3)
    val d = sequenceOf("a", "b")
    val e = sortedMapOf("1" to 1)
}

// PLATFORM: Js
// FILE: js.kt

fun jsThings() {
    val c = listOf(1, 2, 3)
    val d = sequenceOf("a", "b")
    val e = linkedStringMapOf("1" to 1)
}
