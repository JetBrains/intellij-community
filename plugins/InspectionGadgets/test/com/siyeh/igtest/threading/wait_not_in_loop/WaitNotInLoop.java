class WaitNotInLoop {

  private final Object lock = new Object();

  public void f() throws InterruptedException {
    lock.<warning descr="Call to 'wait()' is not made in a loop">wait</warning>();
  }
}