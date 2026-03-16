package com.example

import <fold text='...'>androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier</fold>

fun nestedReturnsComposable(condition: Boolean): @Composable () -> Unit <fold text='{...}'>{
  return if (condition) <fold text='{...}'>{
    <fold text='{...}'>{
      val m = <fold text='Modifier.(...)'>Modifier
        .adjust()
        .adjust()</fold>
    }</fold>
  }</fold> else <fold text='{...}'>{
    <fold text='{...}'>{
      val m = <fold text='Modifier.(...)'>Modifier
        .adjust()
        .adjust()</fold>
    }</fold>
  }</fold>
}</fold>

fun notReturnedIfElse(): @Composable () -> Unit <fold text='{...}'>{
  val notReturned = if (true) <fold text='{...}'>{
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
  return {}
}</fold>

fun notReturnedWhen(): @Composable () -> Unit <fold text='{...}'>{
  val notReturned = when (1) <fold text='{...}'>{
    1 -> <fold text='{...}'>{
      <fold text='{...}'>{
        Modifier
          .adjust()
          .adjust()
      }</fold>
    }</fold>
    else -> <fold text='{...}'>{
      <fold text='{...}'>{
        Modifier
          .adjust()
          .adjust()
      }</fold>
    }</fold>
  }</fold>
  return {}
}</fold>

fun lambdaInsideCallInsideIf(): @Composable () -> Unit <fold text='{...}'>{
  if (true) <fold text='{...}'>{
    listOf(1).map <fold text='{...}'>{
      Modifier
        .adjust()
        .adjust()
    }</fold>
  }</fold>
  return {}
}</fold>