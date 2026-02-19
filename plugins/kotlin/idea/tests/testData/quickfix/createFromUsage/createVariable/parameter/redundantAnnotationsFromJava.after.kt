// "Create parameter 'bar233'" "true"
// ERROR: Unresolved reference: foo23
// ERROR: Unresolved reference: bar233

class B(bar233: MutableList<MutableList<@MyNotNull Int>?>) : A(foo23, ba<selection><caret></selection>r233)
// WITH_STDLIB
