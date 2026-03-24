package com.example

import <fold text='...'>androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier</fold>

@Composable
fun ApplyBlock() <fold text='{...}'>{
  val m = Modifier.apply <fold text='{...}'>{
    <fold text='this.(...)'>this
      .adjust()
      .adjust()
      .adjust()</fold>
    }</fold>
}</fold>

@Composable
fun RunBlock() <fold text='{...}'>{
  val m = run <fold text='{...}'>{
    <fold text='Modifier.(...)'>Modifier
      .adjust()
      .adjust()
      .adjust()</fold>
  }</fold>
}</fold>

@Composable
fun WithScope() <fold text='{...}'>{
  val density = 2f
  with(density) <fold text='{...}'>{
    val m = <fold text='Modifier.(...)'>Modifier
      .adjust()
      .adjust()
      .adjust()</fold>
  }</fold>
}</fold>

@Composable
fun LetScope() <fold text='{...}'>{
  val data = "input"
  data.let <fold text='{...}'>{
    val m = <fold text='Modifier.(...)'>Modifier
      .adjust()
      .adjust()
      .adjust()</fold>
  }</fold>
}</fold>