/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
public class MyTest {

    static void m(Integer i) { assertTrue(true); }

    interface I1 {
        void m(int x);
    }

    interface I2 {
        void m(Integer x);
    }

    static void call(Integer i, I1 s) {}
    static void call(int i, I2 s) {}

    public static void main(String[] args) {
        call(1, i -> m(i));
    }
}
