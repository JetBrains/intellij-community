package com.example

import <fold text='...'>androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier</fold>

suspend fun awaitApplication(content: @Composable () -> Unit) {}
suspend fun regularSuspendLambda(block: suspend () -> Unit) {}

suspend fun main() = <fold text='{...}'>awaitApplication <fold text='{...}'>{
  val m = <fold text='Modifier.(...)'>Modifier
    .adjust()
    .adjust()</fold>
}</fold></fold>

suspend fun noFoldOutsideComposable() <fold text='{...}'>{
  val notFolded = Modifier
    .adjust()
    .adjust()

  awaitApplication <fold text='{...}'>{
    val folded = <fold text='Modifier.(...)'>Modifier
      .adjust()
      .adjust()</fold>
  }</fold>
}</fold>

suspend fun noFoldInSuspendLambda() <fold text='{...}'>{
  regularSuspendLambda <fold text='{...}'>{
    val notFolded = Modifier
      .adjust()
      .adjust()
  }</fold>
}</fold>
