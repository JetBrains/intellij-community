// FIX: Use property access syntax
// IGNORE_K2
// Delete the ignore directive after fixing KTIJ-29110

abstract class KotlinClass : JavaInterface {
    override fun getSomething(): String = ""
}

fun foo(k: KotlinClass, p: Any) {
    if (p is String) {
        k.<caret>setSomething(p)
    }
}