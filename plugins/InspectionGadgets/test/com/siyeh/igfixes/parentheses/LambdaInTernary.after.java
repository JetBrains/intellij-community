class Inversion {
  Runnable context() {
    return true ? (<caret>) -> {} : null;
  }
}