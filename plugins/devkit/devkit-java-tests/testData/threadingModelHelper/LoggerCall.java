import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.annotations.RequiresReadLock;

class LoggerCall {
  void testMethod(Logger logger) {
    logger.info("message");
    logger.error("message");
    helper();
  }

  @RequiresReadLock
  void helper() { }
}

class ReadLogger extends Logger {
  @Override
  public void info(String message) {
    read();
  }

  @RequiresReadLock
  void read() { }
}
