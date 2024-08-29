/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestGenerics<A, B extends TestGenerics.Maps & List>  {
  static Map<String, Boolean> field = Maps.newHashMap();
  static List<List<String>> llstring = new ArrayList<>();
  static List<Byte> bytes = new ArrayList<>();
  A[] aArray = (A[])(new Object[10]);

  public void genericAllocation() {
    aArray = (A[])(new Object[20]);
  }
  
  public static void genericInference() {
    HashMap<String, Integer> test = Maps.newHashMap();
  }

  public void genericList() {
    List<B> testList = new ArrayList<B>();
    B b = testList.get(0);
    System.out.println("B:" + b);
  }

  public void genericCast() {
    HashMap<String, Boolean> upcast = (HashMap<String, Boolean>)field;
  }

  public void genericForEach() { 
    for (String s : field.keySet()) {
      System.out.println(s);
    }
  }

  public void genericForEachWithCast() { 
    for (String s : llstring.get(0)) {
      System.out.println(s);
    }
  }

  public <T extends Number> void genericSuperUp() {
    List<T> list = new ArrayList<>();
    for (Number number : bytes) {
      list.add((T)number);
    }
   }

  public static class Maps {
    public static <K, V> HashMap<K, V> newHashMap() {
      return new HashMap<K, V>();
    }
  }
}
