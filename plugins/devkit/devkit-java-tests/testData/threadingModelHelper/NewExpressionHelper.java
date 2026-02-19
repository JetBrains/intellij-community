package test;
import com.intellij.util.concurrency.annotations.RequiresReadLock;

public class Helper {
  public Helper() {
    RequiresReadLock;
  }
}
public class C {
  public void testMethod() { new Helper(); }
}