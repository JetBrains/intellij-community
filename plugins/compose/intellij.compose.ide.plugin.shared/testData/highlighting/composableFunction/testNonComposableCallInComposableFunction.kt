package test

import androidx.compose.runtime.Composable

fun call() = Unit

@Composable
fun context() {
  call<caret>()
}