// "Remove deprecated '@Experimental' annotation" "true"
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_STDLIB

@Experimental<caret>
@RequiresOptIn
annotation class Marker
