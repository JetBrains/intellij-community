// "Optimize imports" "false"
// ACTION: Introduce import alias
// ERROR: Unresolved reference. None of the following candidates is applicable because of receiver type mismatch: <br>public fun Int.doSmth(): Unit defined in one in file notRemoveImportsForResolvedImportAndUnresolvedReference.before.Dependency.kt

import one.doSmth<caret>

fun a() {
    "".doSmth()
}
