class QuickFix {

    private static final class INSTANCEHolder {
        static final Object INSTANCE = new Object();
    }

    public static Object getInstance() {
        return INSTANCEHolder.INSTANCE;
  }
}