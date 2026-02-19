package test

import dependency.one.MyAnnotation
import dependency.two.MyAnnotation

@MyAnnotation
fun test<caret>() {}

// ANNOTATION: