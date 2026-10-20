import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.concurrency.annotations.RequiresReadLock;

class UserDataHolderCall {
  void testMethod(UserDataHolder holder) {
    holder.getUserData("key");
    helper();
  }

  @RequiresReadLock
  void helper() { }
}

class ReadUserDataHolder implements UserDataHolder {
  @Override
  public Object getUserData(Object key) {
    return read();
  }

  @RequiresReadLock
  Object read() {
    return null;
  }
}
