public class Bug {
  interface I1{}
  interface I2{}
  class C1 {}
  class C2 extends C1 implements I1, I2{}
  class C3 extends C1 implements I1, I2{}

  public static void x(C1 c1) {
    if (c1 instanceof I1 && c1 instanceof I2) {

    }

    if (1 > Math.random() && Math.random()==Math.random()) {

    }

    if (!true){

    }
  }
}