import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.util.concurrency.annotations.RequiresReadLock;

class UserDataHolderCall {
  void testMethod(UserDataHolder holder, UserDataHolderEx holderEx, UserDataHolderBase holderBase) {
    holder.getUserData("key");
    holderEx.putUserDataIfAbsent("key", "value");
    holderBase.getUserData("key");
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

class ReadUserDataHolderBase extends UserDataHolderBase {
  @Override
  public Object getUserData(Object key) {
    return read();
  }

  @RequiresReadLock
  Object read() {
    return null;
  }
}

class ReadUserDataHolderEx implements UserDataHolderEx {
  @Override
  public Object getUserData(Object key) {
    return null;
  }

  @Override
  public Object putUserDataIfAbsent(Object key, Object value) {
    return read();
  }

  @RequiresReadLock
  Object read() {
    return null;
  }
}
