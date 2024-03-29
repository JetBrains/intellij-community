// "Add 'val' to parameter 'x'" "true"
// WITH_STDLIB
/* IGNORE_K2 */
@JvmInline
value class Foo(<caret>x: Int)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.AddValVarToConstructorParameterAction$QuickFix