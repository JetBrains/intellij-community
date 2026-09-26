// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
class X {
  <error descr="'var' declarations are available in Groovy 3.0 or later">var</error> field = 1;
  
  <error descr="Modifier 'var' not allowed on methods">var</error> method() {
    for (<error descr="'var' declarations are available in Groovy 3.0 or later">var</error> i = 1; i < 10; i++) {
      
    }
  }
  
}