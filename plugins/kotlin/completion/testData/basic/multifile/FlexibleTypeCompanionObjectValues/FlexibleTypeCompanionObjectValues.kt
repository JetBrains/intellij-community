package a

class KotlinClass {
    companion object {
        val INSTANCE: KotlinClass = KotlinClass()
    }
}

fun test() {
    JavaClass.test(<caret>)
}

// EXIST: INSTANCE
