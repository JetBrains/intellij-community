import java.lang.Process
import java.util.concurrent.TimeUnit

suspend fun suspendingFunction(process: Process) {
  process.<error descr="Use 'com.intellij.util.io.ProcessKt.awaitExit()'">waitFor()</error>
  process.<error descr="Use 'com.intellij.util.io.ProcessKt.awaitExit()'">onExit()</error>
}

fun suspendingLambdaParameter(@Suppress("UNUSED_PARAMETER") l: suspend () -> Unit) {
  // ...
}

fun suspendingLambdaParameterClient(process: Process) {
  suspendingLambdaParameter {
    process.<error descr="Use 'com.intellij.util.io.ProcessKt.awaitExit()'">waitFor()</error>
    process.<error descr="Use 'com.intellij.util.io.ProcessKt.awaitExit()'">onExit()</error>
  }
}

fun suspendingLambdaVariable(process: Process) {
  @Suppress("UNUSED_VARIABLE") val l: suspend () -> Unit = {
    process.<error descr="Use 'com.intellij.util.io.ProcessKt.awaitExit()'">waitFor()</error>
    process.<error descr="Use 'com.intellij.util.io.ProcessKt.awaitExit()'">onExit()</error>
  }
}

fun suspendingLambdaReturnedFromFunction(process: Process): suspend () -> Unit = {
  process.<error descr="Use 'com.intellij.util.io.ProcessKt.awaitExit()'">waitFor()</error>
  process.<error descr="Use 'com.intellij.util.io.ProcessKt.awaitExit()'">onExit()</error>
}

// shouldn't be reported in the cases below:

fun blockingFunction(process: Process) {
  process.waitFor()
  process.onExit()
}

fun blockingLambdaParameter(@Suppress("UNUSED_PARAMETER") l: () -> Unit) {
  // ...
}

fun blockingLambdaParameterClient(process: Process) {
  blockingLambdaParameter {
    process.waitFor()
    process.onExit()
  }
}

fun blockingLambdaVariable(process: Process) {
  @Suppress("UNUSED_VARIABLE") val l: () -> Unit = {
    process.waitFor()
    process.onExit()
  }
}

fun blockingLambdaReturnedFromFunction(process: Process): () -> Unit = {
  process.waitFor()
  process.onExit()
}

fun waitForWithParams(process: Process) {
  process.waitFor(1000L, TimeUnit.MILLISECONDS)
}
