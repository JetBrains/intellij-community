// "Create parameter 'foo1'" "true"
// ERROR: Unresolved reference: foo1

class B(foo1: String?) : A(foo1<selection><caret></selection>)
