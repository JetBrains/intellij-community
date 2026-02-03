/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pkg;

import java.lang.Exception;
import java.lang.RuntimeException;

public class TestTryCatchFinally {
  public void test1(String x) {
    try {
      System.out.println("sout1");
    } catch (Exception e) {
      try {
        System.out.println("sout2");
      } catch (Exception e2) {
        // Empty
        // Empty
        // Empty
      }
    } finally {
      System.out.println("finally");
    }
  }

  int foo(int a) throws Exception {
    if (a < 1) {
      throw new RuntimeException();
    } else if ( a <5) {
      return a;
    }
    else {
      throw new Exception();
    }
  }

  public int test(String a) {
    try {
      return Integer.parseInt(a);
    } catch (Exception e) {
      System.out.println("Error" + e);
    } finally {
      System.out.println("Finally");
    }
    return -1;
  }
}