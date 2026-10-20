import com.intellij.util.concurrency.annotations.RequiresReadLock;

import java.util.List;

class JdkMethodCall {
  void testMethod(List<Object> list, Runnable runnable) {
    list.get(0);
    runnable.run();
    helper();
  }

  @RequiresReadLock
  void helper() { }
}

abstract class ReadList implements List<Object> {
  @Override
  public Object get(int index) {
    return read();
  }

  @RequiresReadLock
  Object read() {
    return null;
  }
}

class ReadRunnable implements Runnable {
  @Override
  public void run() {
    read();
  }

  @RequiresReadLock
  void read() { }
}
