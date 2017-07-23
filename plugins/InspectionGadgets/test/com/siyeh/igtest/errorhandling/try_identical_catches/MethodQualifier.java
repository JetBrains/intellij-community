class Poly {
  Poly rotateRight() {
    return this;
  }

  public Poly rotate(boolean rotateRight) {
    try {
    } catch (RuntimeException e) {
      return rotateRight();
    } catch (Exception e) {
      return rotateRight().rotateRight().rotateRight();
    }
    return null;
  }
}