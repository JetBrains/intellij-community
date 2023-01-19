// "Push down 'switch' expression" "true-preview"
class X {
  void print(int value) {
      System.out.println(switch (value) {
          case 0 -> "zero";
          case 1 -> "one";
          case 2, 3, 4 -> "few";
          default -> throw new IllegalStateException();
      });
  }
}