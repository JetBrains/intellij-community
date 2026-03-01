abstract class Foo private()

var a : Foo = <caret>

// ABSENT: Foo
// ABSENT: object

// IGNORE_K2
