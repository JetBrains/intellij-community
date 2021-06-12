// "Create parameter 'bar23'" "true"
// ERROR: Unresolved reference: foo23
// ERROR: Unresolved reference: bar23

class B(bar23: MutableList<MutableList<@MyNotNull Int>?>) : A(foo23, bar23)