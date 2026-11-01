import com.intellij.openapi.progress.ProgressManager;

class InsertRethrowHelperInBroadCatch {
  void m() {
    try {
      ProgressManager.checkCanceled();
    }
    catch (Throwable <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown. It is thrown by 'checkCanceled()'."><caret>e</error>) {
      handle(e);
    }
  }

  void handle(Throwable e) {}
}
