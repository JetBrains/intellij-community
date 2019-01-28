class ThisVariable {

  private ThisVariable getRoot() {
      ThisVariable result = this;
      while (true) {
          if (result.getParent() instanceof ThisVariable) {
              result = ((ThisVariable) result.getParent());
              continue;
          }
          return result;
      }
  }

  public Object getParent() {
    return null;
  }
}