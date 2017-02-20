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
import java.util.List;

public class ForLoopThatDoesntUseLoopVariable {
  boolean test(int i) {
    return i < 10;
  }

  void bug() {
    for (int i = 0; test(i); i++) {
      <warning descr="'for' statement has update which does not use the for loop variable">for</warning> (int j = 0; test(j); i++) {
        System.out.println(i + ":" + j);
      }
    }
  }

  void bug2() {
    for (int i = 0; test(i); i++) {
      <warning descr="'for' statement has condition which does not use the for loop variable">for</warning> (int j = 0; test(i); j++) {
        System.out.println(i + ":" + j);
      }
    }
  }

  // IDEA-166869
  void test(List<String> lines) {
    int i = 0;
    for (int size = lines.size(); i < size; i++) {
      if (lines.get(i).isEmpty()) break;
    }
    System.out.println(i);
  }

  void test2(List<String> lines) {
    int i = 0, j = 0;
    <warning descr="'for' statement has update which does not use the for loop variable">for</warning> (int size = lines.size(); i < size; j++) {
      if (lines.get(i).isEmpty()) break;
    }
    System.out.println(j);
  }
}
