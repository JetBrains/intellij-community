// FIR_IDENTICAL
// FIR_COMPARISON
fun main() {
    read<caret>
}

// LANGUAGE_VERSION: 1.5
// EXIST: { itemText: "readLine" }
// ABSENT: { itemText: "readlnOrNull" }
// ABSENT: { itemText: "readln" }
