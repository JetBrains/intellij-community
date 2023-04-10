// "Replace with a comparison of 'get()' call results" "true-preview"
import java.util.concurrent.atomic.AtomicInteger;

class Test {
  public void testAtomicInteger(AtomicInteger a1, AtomicInteger a2) {
    if (!(a1.get() == a2.get())) {
      System.out.println("Strange");
    }
  }
}