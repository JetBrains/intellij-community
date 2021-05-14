import java.util.function.IntSupplier;

class Main {
  void test(int i) {
      IndexOutOfBoundsException e = new IndexOutOfBoundsException(i);
      i++;
      throw e;
  }
}