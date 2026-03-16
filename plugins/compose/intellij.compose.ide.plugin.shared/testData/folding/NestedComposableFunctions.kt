package com.example

import <fold text='...'>androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier</fold>

@Composable
fun Level1() <fold text='{...}'>{
  @Composable
  fun Level2() <fold text='{...}'>{
    @Composable
    fun Level3() <fold text='{...}'>{
      <fold text='Modifier.(...)'>Modifier
        .adjust()
        .adjust()
        .adjust()</fold>
    }</fold>
  }</fold>
}</fold>