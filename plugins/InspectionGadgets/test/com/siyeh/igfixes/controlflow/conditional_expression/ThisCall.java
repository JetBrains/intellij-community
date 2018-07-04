class ThisCall {

  ThisCall(int i) {
    this(i > 2 <caret>? true : false);
  }

  ThisCall(boolean b) {}
}