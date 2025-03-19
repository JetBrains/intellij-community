package test

import dependency.MyAnnotation

@MyAnnotation
fun test<caret>() {}

// ANNOTATION: dependency/MyAnnotation