// ALLOW_ERRORS
val fgs = MyDependency(classifier = "")
val dva = fgs.copy(classifier = "")
val dva2 = fgs.copy(<!HIGHLIGHTING("severity='ERROR'; descr='[NAMED_PARAMETER_NOT_FOUND] No parameter with name 'abc' found.'")!>abc<!> = "")
