// "Replace deprecated '@Experimental' annotation with '@RequiresOptIn'" "true"
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
// WITH_STDLIB

import kotlin.RequiresOptIn.*

@Experimental<caret>(Experimental.Level.ERROR)
annotation class Marker
