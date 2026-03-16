package com.example

import <fold text='...'>androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier</fold>

inline fun fold(noinline a: @Composable () -> Unit) {}
inline fun noFold(noinline block: () -> Unit) { block() }

@Composable
fun ComposableFunction() <fold text='{...}'>{

  fold <fold text='{...}'>{
    <fold text='Modifier.(...)'>Modifier
      .adjust()
      .adjust()</fold>
  }</fold>

  noFold <fold text='{...}'>{
    Modifier
      .adjust()
      .adjust()
  }</fold>

}</fold>