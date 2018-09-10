public class FinallyBlockCannotCompleteNormally extends Throwable {
  public static void main(String[] args) {
    try {
      throw new RuntimeException();
    } finally {
      System.exit(0);
    }
  }

  int goo() {
    try {
      throw new RuntimeException();
    } <warning descr="'finally' block can not complete normally">finally</warning> {
      System.exit(0);
    }
  }
}