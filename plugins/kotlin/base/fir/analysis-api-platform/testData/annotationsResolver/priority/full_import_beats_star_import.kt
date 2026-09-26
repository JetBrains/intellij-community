package test

import dependency.one.MyAnnotation
import dependency.two.*

@MyAnnotation
fun test<caret>() {}

// ANNOTATION: dependency/one/MyAnnotation