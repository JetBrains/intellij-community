// "Replace deprecated '@Experimental' annotation with '@RequiresOptIn'" "true"
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn -Xexperimental
// WITH_RUNTIME

@Experimental<caret>(level=Experimental.Level.WARNING)
annotation class Marker
