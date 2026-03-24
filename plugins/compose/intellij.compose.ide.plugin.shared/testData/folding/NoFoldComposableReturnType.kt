package com.example

import <fold text='...'>androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier</fold>

fun notReturningComposable(): () -> Unit <fold text='{...}'>{
  return <fold text='{...}'>{
    Modifier
      .adjust()
      .adjust()
  }</fold>
}</fold>

fun nonComposableReturn(condition: Boolean): () -> Unit <fold text='{...}'>{
  return if (condition) <fold text='{...}'>{
    <fold text='{...}'>{
      Modifier
        .adjust()
        .adjust()
    }</fold>
  }</fold> else <fold text='{...}'>{
    <fold text='{...}'>{
      Modifier
        .adjust()
        .adjust()
    }</fold>
  }</fold>
}</fold>