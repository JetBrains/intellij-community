import com.intellij.util.concurrency.annotations.RequiresReadLock;

public class RepeatedPolymorphicCall {
  public void testMethod(Worker worker) {
    first(worker);
    second(worker);
  }

  void first(Worker worker) {
    worker.work();
  }

  void second(Worker worker) {
    worker.work();
  }
}

interface Worker {
  void work();
}

class ReadWorker implements Worker {
  @Override
  @RequiresReadLock
  public void work() { }
}
