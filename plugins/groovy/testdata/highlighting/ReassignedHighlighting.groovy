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
def <info descr="null">method</info>(int <info descr="null">param1</info>, int <info descr="null">param2</info>) {
int <info descr="null">var1</info> = 0
int <info descr="null">var2</info> = 1
int <info descr="null">var3</info> = 1
if (<info descr="null">param1</info> == 1) {
<info descr="null">param2</info> = <info descr="null">var2</info> = 2
<info descr="null">var3</info>++
  }
  <info descr="null">println</info> <info descr="null">var1</info> +
     <info descr="null">var2</info> +
<info descr="null">param1</info> +
     <info descr="null">param2</info>
}

<info descr="null">method</info>(239, 42)