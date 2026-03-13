package pkg;

import pkg.res.Loader;

public class Main {
   public static void main(String[] args) {
      Loader loader = new Loader();
      String resource = loader.getResource();
      System.out.println(resource);
   }
}
