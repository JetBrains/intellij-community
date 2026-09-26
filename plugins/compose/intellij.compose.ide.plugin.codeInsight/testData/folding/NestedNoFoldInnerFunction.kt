package com.example

import <fold text='...'>androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier</fold>

@Composable
fun ComposableFunction() <fold text='{...}'>{
  fun NonComposableFunction() <fold text='{...}'>{
    val c = Modifier
      .adjust()
      .adjust()
  }</fold>
}</fold>

object MyObject <fold text='{...}'<fold text='{...}'>{
  @Composable
  fun ComposableFunction() <fold text='{...}'>{
    fun NonComposableFunction() <fold text='{...}'>{
      val c = Modifier
        .adjust()
        .adjust()
    }</fold>
  }</fold>
}</fold>