import testdata.AlreadyImported
import testdata.Foo
import testdata.Container.NestedFoo

fun test() {
    val value: Foo = <caret>
}

// ORDER: NestedFoo
// ORDER: object
// ORDER: TopLevelFoo
