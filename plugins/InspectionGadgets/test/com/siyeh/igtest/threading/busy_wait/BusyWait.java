class X {
  volatile int x;
  
  public void foo() throws Exception {
    while (x > 0) {
      Thread.<warning descr="Call to 'Thread.sleep()' in a loop, probably busy-waiting">sleep</warning>(10);
    }
    while (x > 0) {
      Thread.<warning descr="Call to 'Thread.sleep()' in a loop, probably busy-waiting">sleep</warning>(10, 20);
    }
    int y = 2;
    while (y > 0) {
      Thread.sleep(10);
      y--;
    }
  }
  
  public boolean waitTillFileRemoval(File file) throws Exception {
    int attempts = 5;
    while (file.exists() && attempts-- > 0) {
      Thread.sleep(100);
    }
    return file.exists();
  }
  
  interface File {
    boolean exists();
  }
}