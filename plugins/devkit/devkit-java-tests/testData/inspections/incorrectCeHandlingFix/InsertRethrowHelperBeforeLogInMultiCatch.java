import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;

class InsertRethrowHelperBeforeLogInMultiCatch {
  private static final Logger LOG = Logger.getInstance(InsertRethrowHelperBeforeLogInMultiCatch.class);

  void m() {
    try {
      // anything
    }
    catch (IllegalArgumentException | ProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged"><caret>LOG.error(e)</error>;
    }
  }
}
