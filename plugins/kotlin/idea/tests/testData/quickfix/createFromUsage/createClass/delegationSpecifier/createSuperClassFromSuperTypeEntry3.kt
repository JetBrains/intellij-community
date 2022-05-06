// "Create class 'Unknown'" "true"
// ACTION: Create class 'Unknown'
// ACTION: Create interface 'Unknown'
// ACTION: Create type parameter 'Unknown' in class 'A'
// ACTION: Do not show return expression hints
// DISABLE-ERRORS
class A() : Unknown<caret> {
    constructor(i: Int) : this()
}