// IS_APPLICABLE: true
// K2_ERROR: 'val' cannot be reassigned.
// K2_ERROR: Property must be initialized or be abstract.

object Foo {
    val <caret>prop: Boolean

    init  {
        Foo.prop = true
    }
}