// "Push down 'switch' expression" "false"
class X {
  void test(int e) {
    <caret>switch (e) {
      case 1 -> System.out.println(1);
      case 2 -> System.out.println(2);
    }
  }
}