// "Add 'Any' as upper bound for T to make it non-nullable" "false"
// ERROR: Type mismatch: inferred type is Enum<*> but Enum<Enum<*>> was expected
// ERROR: Type mismatch: inferred type is Enum<*> but Enum<in Enum<*>> was expected
// ACTION: Change parameter 'enumClass' type of primary constructor of class 'Foo' to 'Class<Enum<*>>'
// ACTION: Create function 'Foo'
// ACTION: Create secondary constructor
// ACTION: Introduce import alias
// LANGUAGE_VERSION: 1.7

class Test

class Foo<T : Enum<T>>(private val enumClass: Class<T>) {
    fun test() {}
}

fun bar() {
    Foo(<caret>Test::class as Class<Enum<*>>).test()
}
