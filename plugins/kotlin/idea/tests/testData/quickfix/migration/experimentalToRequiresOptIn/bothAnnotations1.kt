// "Remove deprecated '@Experimental' annotation" "true"
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_STDLIB 1.7.0

@Experimental<caret>
@RequiresOptIn
annotation class Marker
