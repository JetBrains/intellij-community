interface Foo

class CandidateClass : Foo
object Bar : Foo
typealias CandidateObject = Bar

fun foo(f: Foo) {
    if (f == Candidate<caret>) {
    }
}

// ORDER: CandidateObject
