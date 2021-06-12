class QuickFix {
  private Object INSTANCE;

  public Object getInstance() {
    <warning descr="Double-checked locking"><caret>if</warning> (INSTANCE == null) {
      synchronized (QuickFix.class) {
        if (INSTANCE == null) {
          INSTANCE = new Object();
        }
      }
    }
    return INSTANCE;
  }
}