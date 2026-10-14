interface Foo

class Holder {
    class CandidateClass : Foo
    companion object CandidateObject : Foo
}

fun foo(f: Foo) {
    if (Holder.Candidate<caret> != f) {
    }
}

// ORDER: CandidateObject
