// IGNORE_K1
class Foo

object Bar

fun test(a: Any) {
    when (a) {
        i<caret>
    }
}

// EXIST: is String
// EXIST: is Foo
// EXIST: is Bar
// ABSENT: Bar
// EXIST: buildList