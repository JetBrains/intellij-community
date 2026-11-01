import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;

class RemoveLoggingStatement {
  private static final Logger LOG = Logger.getInstance(RemoveLoggingStatement.class);

  void m() {
    try {
      System.out.println();
    }
    catch (ProcessCanceledException e) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged"><caret>LOG.error(e)</error>;
      throw e;
    }
  }
}
