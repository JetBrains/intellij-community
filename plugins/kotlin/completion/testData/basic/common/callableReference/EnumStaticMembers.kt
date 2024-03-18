// IGNORE_K1
enum class EE {
    AA
}

fun test() {
    EE::<caret>
}

// EXIST: { itemText: "values", attributes: "bold" }
// ABSENT: AA