class Comments2 {

  public int methodThree(final int data) {
    int alpha<caret> = (data) + 5; // !!!
    if (data == 0) {
      throw new IllegalArgumentException("DATA_CANT_BE_ZERO");
    }
    return process(alpha);
  }

  private int process(final int alphaData) {
    return alphaData + alphaData;
  }
}