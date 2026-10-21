interface Foo

class CandidateClass : Foo
object CandidateObject : Foo

fun foo(f: Foo) = f as Candidate<caret>

// ORDER: CandidateClass
// ORDER: CandidateObject
