// "Push down 'switch' expression" "true-preview"
class X {
  void print(int value) {
      System.out.println(switch (value) {
          case 2 -> "few";
          case 3 -> "few";
          case -1 -> "zero";
          case 0 -> "zero";
          case 1 -> "one";
          case 4 -> "few";
          default -> throw new IllegalStateException();
      });
  }
}