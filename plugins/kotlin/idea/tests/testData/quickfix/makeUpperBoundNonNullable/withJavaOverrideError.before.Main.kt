// "Add 'Any' as upper bound for T to make it non-nullable" "true"
// ERROR: 'foo' overrides nothing
// LANGUAGE_VERSION: 1.8

class FooChild<T> : Foo<T>() {
    override<caret> fun foo(x: T) {}
}
