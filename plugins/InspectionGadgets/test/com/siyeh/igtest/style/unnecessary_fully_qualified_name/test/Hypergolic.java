class Hypergolic {

  interface Appendable {}
}
class RocketFuel extends Hypergolic {

  void x() {
    new java.lang.Appendable() {
      @Override
      public java.lang.Appendable append(CharSequence csq) throws IOException {
        return null;
      }

      @Override
      public java.lang.Appendable append(CharSequence csq, int start, int end) throws IOException {
        return null;
      }

      @Override
      public java.lang.Appendable append(char c) throws IOException {
        return null;
      }
    };
  }
}