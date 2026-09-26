// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: usages, constructorUsages, expected

actual class <caret>My(val s: String) {
    actual fun boo() {}
}
