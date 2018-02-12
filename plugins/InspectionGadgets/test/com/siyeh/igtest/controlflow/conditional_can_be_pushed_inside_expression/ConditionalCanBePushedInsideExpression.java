import java.util.Random;

class ConditionalCanBePushedInsideExpression {

  void fuzzy() {
    String someString = <warning descr="Conditional expression can be pushed inside branch">new Random().nextBoolean() ? "2" + "q" + "1" : "2" + "qwe" + "1"</warning>;
  }

  void fuzzy2() {
    Object someString = <warning descr="Conditional expression can be pushed inside branch">new Random().nextBoolean() ? (Object) "1" : (Object) "2"</warning>;
  }

  void fuzzy3() {
    Object someString = <warning descr="Conditional expression can be pushed inside branch">new Random().nextBoolean() ? "21" + (Object) "1" : "21" + (Object) "2"</warning>;
  }

  void fuzzy4(int[] ints) {
    int i = <warning descr="Conditional expression can be pushed inside branch">new Random().nextBoolean() ? ints[3] : ints[4]</warning>;
  }

  void fuzzy5(String[] strings) {
    String s = <warning descr="Conditional expression can be pushed inside branch">new Random().nextBoolean()? "asd" + strings[2] : "qwe" + strings[2]</warning>;
  }

  void fuzzy6() {
    int j = <warning descr="Conditional expression can be pushed inside branch">new Random().nextBoolean() ? 6 + someMethod("123", "") : 6 + someMethod("321", "")</warning>;
  }

  void fuzzy7(int k) {
    int i = k == 10 ? singleParameterMethod("one") : singleParameterMethod("two");
  }

  int someMethod(String s, String s2) {
    return s.length();
  }

  int singleParameterMethod(String s) {
    return s.length();
  }

  class Item {
    Item(String name) {
    }

    Item(int value) {
    }

    void v() {
      int i = 1;
      Item item = (i == 1 ? new Item("1") : new Item(i)); // warning here

      Item item1 = (<warning descr="Conditional expression can be pushed inside branch">i == 1 ? new Item("1") : new Item("2")</warning>); // warning here
    }
  }

  void bar(boolean b){
    String s = <warning descr="Conditional expression can be pushed inside branch">b ? foo(bar("true"), true).substring(1) : foo(bar("false"), true).substring(1)</warning>;
    String t = <warning descr="Conditional expression can be pushed inside branch">b ? foo("true", true).substring(1).substring(0) : foo("true", true).substring(2).substring(0)</warning>;
    String u = <warning descr="Conditional expression can be pushed inside branch">b ? foo("true", true).substring(0) : foo("false", true).substring(0)</warning>;
    String v = <warning descr="Conditional expression can be pushed inside branch">b ? bar(bar("one")) : bar(bar("two"))</warning>;
  }

  String foo(String p, boolean b) {return p;}
  String bar(String s) {
    return s;
  }

}