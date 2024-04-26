// PROBLEM: none
// WITH_STDLIB
// IGNORE_FE10_BINDING_BY_FIR

suspend<caret> fun y() {
    suspend {  }()
}