// "Replace with 'stopKoin()'" "true"
// WITH_STDLIB

package com.example.pkg

import com.example.pkg.StandAloneContext.closeKoin

object StandAloneContext {
    @Deprecated(
        "Renamed, use stopKoin() instead.",
        ReplaceWith("stopKoin()", "com.example.pkg.StandAloneContext.stopKoin")
    )
    fun closeKoin() = stopKoin()

    fun stopKoin() {}
}


fun koin() {
    <caret>closeKoin()
}