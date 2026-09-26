import testdata.AlreadyImported
import testdata.Foo

fun test() {
    val value: Foo = <caret>
}

// ORDER: object
// ORDER: TopLevelFoo
// ORDER: NestedFoo
