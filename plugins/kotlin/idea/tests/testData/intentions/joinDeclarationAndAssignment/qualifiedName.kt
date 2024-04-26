// IS_APPLICABLE: true
object Foo {
    val <caret>prop: Boolean

    init  {
        Foo.prop = true
    }
}