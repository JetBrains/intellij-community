interface Foo

class CandidateClass : Foo
object CandidateObject : Foo

fun foo(f: Foo) {
    if (f is Candidate<caret>) {
    }
}

// ORDER: CandidateClass
// ORDER: CandidateObject
