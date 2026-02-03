class BitImage {
  boolean[] data
  int width

  int getHeight() {
    Math.acos(<caret>data.length / width)
  }
}