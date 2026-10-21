interface Foo

class CandidateClass : Foo
object Bar : Foo
typealias BarAlias = Bar
typealias CandidateObject = BarAlias

fun foo(f: Foo) {
    if (f == Candidate<caret>) {
    }
}

// ORDER: CandidateObject
