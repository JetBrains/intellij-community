// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
class Base {
  def Base(int x = 0, int y=5){}
}

class Inheritor extends Base{
}

class Base2 {
  def Base2(int x){}
}

<error descr="No no-arg constructor found in class 'Base2'">class Inheritor2 extends Base2</error> {

}