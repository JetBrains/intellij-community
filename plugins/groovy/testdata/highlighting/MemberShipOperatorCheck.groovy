/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
interface I1 {}
interface I2 {}

def foo(I1 a, I2 b) {
  print (a in b)
}

print (1 in [1])
print (<warning descr="'ArrayList<String>' cannot contain 'Integer'">1 in ['a']</warning>)
print (1 in 1)
print (<warning descr="'ArrayList<Integer>' cannot contain 'ArrayList<Integer>'">[2] in [2]</warning>)

print (1 in new ArrayList())
print (<warning descr="'Integer' cannot contain 'Date'">new Date() in 2</warning>)
print (<warning descr="'ArrayList<Integer>' cannot contain 'String'">'a' in [1]</warning>)