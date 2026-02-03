package test

import androidx.compose.runtime.Composable

@Composable
fun call() = Unit

fun context() {
  call<caret>()
}
