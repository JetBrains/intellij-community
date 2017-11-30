// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.exceptions.detail;

class Test {

  void foo() {
      try {
          if (true) {
              throw new IllegalArgumentException();
          } else {
              throw new NullPointerException();
          }
      } catch (IllegalArgumentException e) {

      } catch (NullPointerException e) {

      }
  }
}