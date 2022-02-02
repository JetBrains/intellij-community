// "Remove deprecated '@Experimental' annotation" "true"
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_STDLIB

@Experimental<caret>(level = Experimental.Level.WARNING)
@RequiresOptIn(message = "Some experimental API", level = RequiresOptIn.Level.ERROR)
annotation class Marker
