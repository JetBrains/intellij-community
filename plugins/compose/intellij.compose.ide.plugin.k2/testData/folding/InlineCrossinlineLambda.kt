package com.example

import <fold text='...'>androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier</fold>

inline fun fold(crossinline a: @Composable () -> Unit) {}
inline fun noFold(crossinline block: () -> Unit) { block() }

@Composable
fun ComposableFunction() <fold text='{...}'>{
  noFold <fold text='{...}'>{
    Modifier
      .adjust()
      .adjust()
  }</fold>

  fold <fold text='{...}'>{
     <fold text='Modifier.(...)'>Modifier
      .adjust()
      .adjust()</fold>
  }</fold>
}</fold>