// FIR_IDENTICAL
// FIR_COMPARISON
fun main() {
    read<caret>
}

// LANGUAGE_VERSION: 1.6
// WITH_ORDER
// EXIST: { itemText: "readln" }
// EXIST: { itemText: "readlnOrNull" }
// EXIST: { itemText: "readLine" }
