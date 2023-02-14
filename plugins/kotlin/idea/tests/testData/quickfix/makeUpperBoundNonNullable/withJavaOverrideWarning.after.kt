// "Add 'Any' as upper bound for T to make it non-nullable" "true"
// LANGUAGE_VERSION: 1.6

class FooChild<T : Any> : Foo<T>() {
    override fun foo(x: T) {}
}
