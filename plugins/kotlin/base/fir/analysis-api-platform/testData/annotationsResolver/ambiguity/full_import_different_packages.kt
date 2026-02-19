package test

import dependency.one.*
import dependency.two.*

@MyAnnotation
fun test<caret>() {}

// ANNOTATION: