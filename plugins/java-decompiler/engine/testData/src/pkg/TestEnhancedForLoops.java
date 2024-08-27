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

import java.util.List;
import java.util.ArrayList;

public class TestEnhancedForLoops {
  public void forArray() {
    int[] numbers = {1, 2, 3, 4, 5, 6};
    for (int number : numbers) {
      System.out.println(number);
    }
  }

  public void forItterator() {
    List<String> strings = new ArrayList<>();
    for (String string : strings) {
      System.out.println(string);
    }
  }

  public void forItteratorUnboxing() {
    List<Integer> ints = new ArrayList<>();
    for (int i : ints) {
      System.out.println("Value: " + i);
    }
  }
}
