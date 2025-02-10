package test

import androidx.compose.runtime.Composable

@Composable
fun call() = Unit


class Foo {
  @Composable
  fun context() {
    call<caret>()
  }
}
