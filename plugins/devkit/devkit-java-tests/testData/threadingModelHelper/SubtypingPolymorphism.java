import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.concurrency.ThreadingAssertions;

public class SubtypingPolymorphism {
  public void testMethod() {
    Service[] services = {new FileService(), new UIService(), new DatabaseService()};
    for (Service service : services) {
      service.execute();
    }
  }
}

interface Service {
  void execute();
}

class FileService implements Service {
  @Override
  @RequiresReadLock
  public void execute() { }
}

class UIService implements Service {
  @Override
  public void execute() {
    ThreadingAssertions.assertEventDispatchThread();
  }
}

class DBService implements Service {
  @Override
  public void execute() {
    ThreadingAssertions.assertReadAccess();
  }
}