class Holder {
    class CandidateClass
    object CandidateObject
}

val reference = Holder.Candidate<caret>::class

// ORDER: CandidateClass
// ORDER: CandidateObject
