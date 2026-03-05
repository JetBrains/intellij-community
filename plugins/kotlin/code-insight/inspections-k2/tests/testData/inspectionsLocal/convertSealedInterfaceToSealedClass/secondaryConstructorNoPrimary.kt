// PROBLEM: none
sealed interface <caret>Result
class Failure : Result {
    constructor(message: String)
    constructor(code: Int)
}