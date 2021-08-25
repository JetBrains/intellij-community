// "" "false"
// ACTION: Add constructor parameter 's'
// ACTION: Create secondary constructor
// ACTION: Remove parameter 's'
// ERROR: No value passed for parameter 's'
open class A(s: String)
class B : A()<caret>
