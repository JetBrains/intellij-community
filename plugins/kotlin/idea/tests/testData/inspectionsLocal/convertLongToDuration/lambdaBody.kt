// WITH_COROUTINES

import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.SelectBuilder

fun test(builder: SelectBuilder<Unit>) {
    builder.onTimeo<caret>ut(1000) {
        doSomething()
    }
}

fun doSomething() {}