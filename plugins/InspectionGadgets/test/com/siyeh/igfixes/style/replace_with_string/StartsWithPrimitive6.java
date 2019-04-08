class StartsWithPrimitive2 {

  String foo(String str) {
    return new Strin<caret>gBuilder().append(5+6).append(7).toString();
  }
}