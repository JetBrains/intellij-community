package test

import androidx.compose.runtime.Composable

@Composable
fun call() = Unit

@Composable
fun context() {
  call<caret>()
}
