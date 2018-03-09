interface Super {
  String FOO = "";
}

class Child implements Super {}

class Bar {
  {
      /*some comment*/
      String s = Super.FOO;
  }
}