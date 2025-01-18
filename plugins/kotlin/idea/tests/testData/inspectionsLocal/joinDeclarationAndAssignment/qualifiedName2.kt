// IS_APPLICABLE: true
// K2-ERROR: 'val' cannot be reassigned.
// K2-ERROR: Property must be initialized or be abstract.

package pack

object Foo {
    val <caret>prop: Boolean

    init  {
        pack.Foo.prop = true
    }
}