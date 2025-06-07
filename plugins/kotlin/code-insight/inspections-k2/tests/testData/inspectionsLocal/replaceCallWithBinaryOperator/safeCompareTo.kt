// PROBLEM: none
// K2_ERROR: Operator call is prohibited on a nullable receiver of type 'Int?'. Use '?.'-qualified call instead.
// ERROR: Operator call corresponds to a dot-qualified call 'nullable?.compareTo(1).compareTo(0)' which is not allowed on a nullable receiver 'nullable?.compareTo(1)'.

val nullable: Int? = null
val x = nullable?.compareTo<caret>(1) >= 0