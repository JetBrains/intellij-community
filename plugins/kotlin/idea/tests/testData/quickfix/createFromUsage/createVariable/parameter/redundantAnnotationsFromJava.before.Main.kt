// "Create parameter 'bar233'" "true"
// ERROR: Unresolved reference: foo23
// ERROR: Unresolved reference: bar233

class B() : A(foo23, ba<caret>r233)
// WITH_STDLIB