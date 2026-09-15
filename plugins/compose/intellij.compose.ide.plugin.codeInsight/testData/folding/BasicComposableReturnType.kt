package com.example

import <fold text='...'>androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier</fold>

fun returnsComposable(): @Composable () -> Unit <fold text='{...}'>{
  return <fold text='{...}'>{
    <fold text='Modifier.(...)'>Modifier
      .adjust()
      .adjust()</fold>
  }</fold>
}</fold>

fun returnsComposableWithParam(): @Composable (Int) -> Unit <fold text='{...}'>{
  return <fold text='{...}'>{
    <fold text='Modifier.(...)'>Modifier
      .adjust()
      .adjust()
      .adjust()</fold>
  }</fold>
}</fold>

fun directReturn(): @Composable () -> Unit <fold text='{...}'>{
  return <fold text='{...}'>{
    <fold text='Modifier.(...)'>Modifier
      .adjust()
      .adjust()</fold>
  }</fold>
}</fold>

fun foo(): Sequence<@Composable () -> Unit> = <fold text='{...}'>(0..10)
  .asSequence()
  .map <fold text='{...}'>{ return@map <fold text='{...}'>{
    <fold text='Modifier.(...)'>Modifier
      .adjust()
      .adjust()</fold>
  }</fold>
}</fold></fold>