package pkg;

class DollarInMemberType {
  void use(Outer.$less$colon$less<String> evidence, Outer.Middle.Inner nested) { }
}

class Outer {
  static class $less$colon$less<T> { }
  static class Middle {
    static class Inner { }
  }
}
