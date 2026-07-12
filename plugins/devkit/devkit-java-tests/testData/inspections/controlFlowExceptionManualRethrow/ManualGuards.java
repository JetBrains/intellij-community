import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;

import java.util.concurrent.CancellationException;

class ManualGuards {
  void controlFlowGuard(Throwable e) throws Throwable {
    <weak_warning descr="Rethrow guard can be replaced with 'rethrowControlFlowException()'">if (e instanceof ControlFlowException) throw e;</weak_warning>
  }

  void processCanceledGuard(Throwable e) throws Throwable {
    <weak_warning descr="Rethrow guard can be replaced with 'rethrowControlFlowException()'">if (e instanceof ProcessCanceledException) throw e;</weak_warning>
  }

  void cancellationExceptionGuard(Throwable e) throws Throwable {
    <weak_warning descr="Rethrow guard can be replaced with 'rethrowControlFlowException()'">if (e instanceof CancellationException) throw e;</weak_warning>
  }

  void shouldRethrowGuard(Throwable e) throws Throwable {
    <weak_warning descr="Rethrow guard can be replaced with 'rethrowControlFlowException()'">if (Logger.shouldRethrow(e)) throw e;</weak_warning>
  }

  void combinedGuard(Throwable e) throws Throwable {
    <weak_warning descr="Rethrow guard can be replaced with 'rethrowControlFlowException()'">if (e instanceof CancellationException || e instanceof ControlFlowException) throw e;</weak_warning>
  }

  void unrelatedTypeNotFlagged(Throwable e) throws Throwable {
    if (e instanceof IllegalArgumentException) throw e;
  }

  void unrelatedConditionNotFlagged(Throwable e, boolean cond) throws Throwable {
    if (cond) throw e;
  }
}
