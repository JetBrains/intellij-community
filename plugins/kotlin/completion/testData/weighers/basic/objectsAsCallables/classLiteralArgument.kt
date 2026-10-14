interface Foo

class CandidateClass : Foo
object CandidateObject : Foo

fun value(f: Foo) = f

val reference = value(Candidate<caret>)::class

// ORDER: CandidateObject
