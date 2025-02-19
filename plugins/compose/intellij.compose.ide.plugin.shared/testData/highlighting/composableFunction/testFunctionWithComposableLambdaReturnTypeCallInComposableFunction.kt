package test

import androidx.compose.runtime.Composable

fun myFunction(): @Composable () -> Unit = {}

@Composable
fun context() {
  val result = myFunction<caret>()
}