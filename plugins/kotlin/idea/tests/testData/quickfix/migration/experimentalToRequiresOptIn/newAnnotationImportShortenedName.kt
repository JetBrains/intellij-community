// "Replace deprecated '@Experimental' annotation with '@RequiresOptIn'" "true"
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
// WITH_RUNTIME

import kotlin.RequiresOptIn.*

@Experimental<caret>(Experimental.Level.ERROR)
annotation class Marker
