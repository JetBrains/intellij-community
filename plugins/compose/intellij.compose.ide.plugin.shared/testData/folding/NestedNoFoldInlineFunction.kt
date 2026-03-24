package com.example

import <fold text='...'>androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.ui.Modifier</fold>

inline fun <T> myRun(block: () -> T): T = block()
fun <T> myRunNonInline(block: () -> T): T = block()

inline fun f(block: @DisallowComposableCalls () -> Unit) = Unit

@Composable
fun composableFunction() <fold text='{...}'>{
  // Non inline
  myRunNonInline <fold text='{...}'>{
    val notFolded = Modifier
      .adjust()
      .adjust()
  }</fold>

  // Inline inside non inline
  myRunNonInline <fold text='{...}'>{
    myRun <fold text='{...}'>{
       val notFolded = Modifier
         .adjust()
         .adjust()
     }</fold>
  }</fold>

  // DisallowComposableCalls block
  f <fold text='{...}'>{
    val notFolded = Modifier
      .adjust()
      .adjust()
  }</fold>
}</fold>

fun NonComposableWithInline() <fold text='{...}'>{
  myRun <fold text='{...}'>{
    val notFolded = Modifier
      .adjust()
      .adjust()
  }</fold>
}</fold>
