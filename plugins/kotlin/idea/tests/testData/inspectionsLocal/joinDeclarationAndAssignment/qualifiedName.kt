// IS_APPLICABLE: true
// K2_ERROR: MUST_BE_INITIALIZED_OR_BE_ABSTRACT
// K2_ERROR: VAL_REASSIGNMENT

object Foo {
    val <caret>prop: Boolean

    init  {
        Foo.prop = true
    }
}