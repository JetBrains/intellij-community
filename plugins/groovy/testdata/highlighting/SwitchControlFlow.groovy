/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
def testV(def variable) {
  def v = "s"
  def m="10"
  def <warning descr="Assignment is not used">x</warning>="10"
  try {
    switch (variable) {
      case 1:
        v = "100"
        break
     case 2:
        break
      default:        
        throw new IllegalArgumentException("Wrong")
    }
  } catch (Exception e) {
    throw e
  }
  return v + m
}