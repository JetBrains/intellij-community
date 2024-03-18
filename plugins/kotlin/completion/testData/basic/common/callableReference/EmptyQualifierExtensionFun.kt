// IGNORE_K1

class C

fun C.extensionFunForC() {}

fun C.test() {
    val v = ::extension<caret>
}

// EXIST: { itemText: "extensionFunForC", attributes: "bold" }