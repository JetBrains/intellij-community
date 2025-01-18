// IS_APPLICABLE: true
// K2-ERROR: 'val' cannot be reassigned.
// K2-ERROR: Property must be initialized or be abstract.

object Foo {
    val <caret>prop: Boolean

    init  {
        Foo.prop = true
    }
}