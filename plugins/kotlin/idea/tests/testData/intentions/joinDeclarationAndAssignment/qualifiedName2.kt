// IS_APPLICABLE: true
package pack

object Foo {
    val <caret>prop: Boolean

    init  {
        pack.Foo.prop = true
    }
}