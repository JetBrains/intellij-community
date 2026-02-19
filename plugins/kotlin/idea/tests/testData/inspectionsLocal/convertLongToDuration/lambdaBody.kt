// WITH_COROUTINES

import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.SelectBuilder
import kotlin.time.Duration.Companion.milliseconds

fun test(builder: SelectBuilder<Unit>) {
    builder.onTimeo<caret>ut(1000) {
        doSomething()
    }
}

fun doSomething() {}