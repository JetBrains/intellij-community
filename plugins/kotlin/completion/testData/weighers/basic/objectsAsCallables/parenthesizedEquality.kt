interface Foo

class CandidateClass : Foo
object CandidateObject : Foo

fun foo(f: Foo) {
    if (f == ((Candidate<caret>))) {
    }
}

// ORDER: CandidateObject
