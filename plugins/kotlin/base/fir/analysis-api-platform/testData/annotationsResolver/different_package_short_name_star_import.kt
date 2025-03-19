package foo.bar.baz

import dependency.*

@Ann1
@Ann2
fun test<caret>() {}

// ANNOTATION: dependency/Ann1, dependency/Ann2