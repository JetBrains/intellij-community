// "Add 'Any' as upper bound for T to make it non-nullable" "true"
// ERROR: 'foo' overrides nothing
// LANGUAGE_VERSION: 1.7

class FooChild<T> : Foo<T>() {
    override<caret> fun foo(x: T) {}
}
