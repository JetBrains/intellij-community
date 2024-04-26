package somepackage

class someClass

val s<caret>

// ABSENT: {"lookupString":"someClass","tailText":" (somepackage)"}
// EXIST: {"lookupString":"someClass","tailText":": someClass (somepackage)"}

