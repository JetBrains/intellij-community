package com.example

import androidx.compose.runtime.Composable

@Composable
fun StringChain() <fold text='{...}'>{
  "Hello"
    .trim()
    .lowercase()
}</fold>