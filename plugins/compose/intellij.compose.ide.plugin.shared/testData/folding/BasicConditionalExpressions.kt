package com.example

import <fold text='...'>androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier</fold>

@Composable
fun ElvisChain(nullableModifier: Modifier?) <fold text='{...}'>{
  val elvisModifier = nullableModifier ?: <fold text='Modifier.(...)'>Modifier
    .adjust()
    .adjust()</fold>
}</fold>

@Composable
fun ConditionalChain(condition: Boolean) <fold text='{...}'>{
  val ifModifier = if (condition) <fold text='Modifier.(...)'>Modifier
    .adjust()
    .adjust()</fold>
  else <fold text='Modifier.(...)'>Modifier
    .adjust()
    .adjust()
    .adjust()</fold>

  val whenModifier = when <fold text='{...}'>{
    condition -> <fold text='Modifier.(...)'>Modifier
      .adjust()
      .adjust()</fold>
    else -> Modifier
  }</fold>
}</fold>