package test

import androidx.compose.runtime.Composable

fun myFunction(): @Composable () -> Unit = {}

fun context() {
  val result = myFunction<caret>()
}