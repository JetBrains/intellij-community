package pkg;

public class TestLoopMerging {
  public float a;
  public float b;
  public float test() {
    while(a - b < -180) b -= 360;
    while(a - b >= 180) b += 360;
    return a;
  }
  public float test2() {
     for (a = 0; a < 10; a++) {
      System.out.println(a);
    }
    for (a = 0; a < 10; a++) {
      System.out.println(a);
    }
    return a;
  }

  public float test3() {
    int[] as = new int[0];
    for (int f: as) {
      f++;
    }
    while(this.a - this.b < -180) this.b -= 360;
    while(this.a - this.b >= 180) this.b += 360;
    for (a = 0; a < 10; a++) {
      System.out.println(a);
    }
    return this.a;
  }
  public float test4() {
    int[] as = new int[0];
    for (int f: as) {
      f++;
      while(this.a - this.b < -180) this.b -= 360;
      while(this.a - this.b >= 180) this.b += 360;
      for (a = 0; a < 10; a++) {
        System.out.println(a);
        while(this.a - this.b < -180) this.b -= 360;
        while(this.a - this.b >= 180) this.b += 360;
      }
    }
    return this.a;
  }
}
