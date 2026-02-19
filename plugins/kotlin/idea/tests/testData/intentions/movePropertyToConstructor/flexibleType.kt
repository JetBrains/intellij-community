// IGNORE_K1

abstract class KotlinClass<T> {
    companion object {
        private fun <T> test(): T = TODO()
    }

    <caret>val propertyWithFlexibleDnnImplicitType =
        JavaClass.wrap(JavaClass.wrap<String, T> { _ -> test() })
}
