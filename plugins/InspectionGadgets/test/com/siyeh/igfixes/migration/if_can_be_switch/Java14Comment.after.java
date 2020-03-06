public class SomeClass {
  int test(char ch) {
      s<caret>witch (ch) {
          case '=' -> {
              System.out.println();
              return 1; // hello
          }
      }
    return 2;
  }
}