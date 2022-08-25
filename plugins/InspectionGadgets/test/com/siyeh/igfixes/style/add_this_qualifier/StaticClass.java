class Outer {
  static class Inner {
    String str;

    String getStr() {
      return str<caret>;
    }
  }
}