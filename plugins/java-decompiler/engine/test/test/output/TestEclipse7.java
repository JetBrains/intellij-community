package test.output;

import java.lang.reflect.Method;

public class TestEclipse7 {

   public void testMultiCatch() {
      try {
         Method e = this.getClass().getDeclaredMethod("foo", new Class[0]);
      } catch (SecurityException | NoSuchMethodException var2) {
         var2.printStackTrace();
      }

   }
}
