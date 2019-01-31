package com.siyeh.igfixes.migration.try_finally_can_be_try_with_resources;

class MyCloseable implements AutoCloseable {
  public MyCloseable(MyCloseable cl) {}

  @Override
  public void close() throws Exception {

  }
}

public class InnerTry {
  void doSome(String f) throws Exception {
      try<caret> (MyCloseable cl2 = new MyCloseable(null); MyCloseable cl1 = new MyCloseable(cl2)) {
      }
  }
}