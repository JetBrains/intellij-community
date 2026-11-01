import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;

class InsertRethrowHelperBeforeLogInBroadCatch {
  private static final Logger LOG = Logger.getInstance(InsertRethrowHelperBeforeLogInBroadCatch.class);

  void m() {
    try {
      ProgressManager.checkCanceled();
    }
    catch (Exception <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown. It is thrown by 'checkCanceled()'.">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged. It is thrown by 'checkCanceled()'."><caret>LOG.error(e)</error>;
    }
  }
}
