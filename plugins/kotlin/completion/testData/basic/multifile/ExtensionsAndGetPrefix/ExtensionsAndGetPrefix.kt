val String.thisFileExtension: Int
    get() = 0

fun f() {
    "".get<caret>
}

// IGNORE_K2
// EXIST: thisFileExtension
// EXIST: notImportedExtension
