class Incomplete1 {

  boolean f(boolean a, boolean b, boolean c) {
    return a <caret>? b : ; //keep me
  }
}