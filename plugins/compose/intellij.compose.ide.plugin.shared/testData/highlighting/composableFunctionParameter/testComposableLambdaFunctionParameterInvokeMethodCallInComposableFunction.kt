package test

import androidx.compose.runtime.Composable

@Composable
fun context(composableLambda: @Composable () -> Unit) {
  composableLambda.invoke<caret>()
}