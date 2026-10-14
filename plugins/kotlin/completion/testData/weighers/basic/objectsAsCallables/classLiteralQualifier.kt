class CandidateClass {
    class Nested
}

object CandidateObject {
    class Nested
}

val reference = Candidate<caret>.Nested::class

// ORDER: CandidateClass
// ORDER: CandidateObject
