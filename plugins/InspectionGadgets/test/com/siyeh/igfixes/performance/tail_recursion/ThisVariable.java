class ThisVariable {

  private ThisVariable getRoot() {
    if (getParent() instanceof ThisVariable)
    {
      return ((ThisVariable) getParent()).<caret>getRoot();
    }
    return this;
  }

  public Object getParent() {
    return null;
  }
}