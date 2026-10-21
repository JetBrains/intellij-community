interface Foo

class Holder {
    class CandidateClass : Foo
    companion object CandidateObject : Foo
}

fun foo(f: Foo) {
    if (f == ((Holder.Candidate<caret>))) {
    }
}

// ORDER: CandidateObject
