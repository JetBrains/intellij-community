package somepackage

class someClass

val s<caret>

// INVOCATION_COUNT: 1
// EXIST: {"lookupString":"someClass","tailText":" (somepackage)"}
// EXIST: {"lookupString":"someClass","tailText":": someClass (somepackage)"}

