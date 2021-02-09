package pkg;

public class TestVariableInitialization {

  public int findMin(int first, int second) {
    int result = 0;
    if (first > second) {
      result = second;
    }
    return result;
  }

  public int findMinimum(int first, int second) {
    int result = 0;
    if (first > second) {
      result = second;
    } else {
      result = first;
    }
    return result;
  }

}
