function foo(a: Integer, b: Integer): String {
  return "";
}

class C extends java.lang.Object, java.io.Serializable {
  var a: Integer;
  def b: String = "C";
  override public function toString() {
    return "{foo(2, 3)}{b}";
  }
}