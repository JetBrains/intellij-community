public class Simple {
  public static void main(String[] args) {
    ThreadLocal<Integer> threadLocal = new ThreadLocal<>();
      /*test2*/
      /*test3*/
      /*test4*/
      (getThreadLocal/*test1*/(threadLocal)).remove(<caret>);
  }

  private static ThreadLocal<Integer> getThreadLocal(ThreadLocal<Integer> threadLocal) {
    return threadLocal;
  }
}
