class Dependency1 {

  public int factorial(int val) {
    return factorial(val, 1);
  }

  private int factorial(int val, int runningVal) {
    if (val == 1) {
      return runningVal;
    } else {
      return (<caret>factorial(val - 1, runningVal * val));
    }
  }
}