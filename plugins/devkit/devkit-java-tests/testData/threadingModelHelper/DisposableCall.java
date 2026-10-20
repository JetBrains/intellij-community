import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.annotations.RequiresReadLock;

class DisposableCall {
  void testMethod(Disposable disposable, ReadDisposable readDisposable, OtherReadDisposable otherReadDisposable) {
    disposable.dispose();
    readDisposable.dispose();
    Disposer.dispose(otherReadDisposable);
    helper();
  }

  @RequiresReadLock
  void helper() { }
}

class ReadDisposable implements Disposable {
  @Override
  public void dispose() {
    read();
  }

  @RequiresReadLock
  void read() { }
}

class OtherReadDisposable implements Disposable {
  @Override
  public void dispose() {
    read();
  }

  @RequiresReadLock
  void read() { }
}
