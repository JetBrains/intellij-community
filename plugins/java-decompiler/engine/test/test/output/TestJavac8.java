package test.output;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;

public class TestJavac8 {

   public static void main(String[] var0) {
      (new TestJavac8()).testLambda();
   }

   public void testTryResources() throws IOException {
      FileReader var1 = new FileReader("file");
      Throwable var2 = null;

      try {
         FileReader var3 = new FileReader("file");
         Throwable var4 = null;

         try {
            System.out.println();
         } catch (Throwable var27) {
            var4 = var27;
            throw var27;
         } finally {
            if(var3 != null) {
               if(var4 != null) {
                  try {
                     var3.close();
                  } catch (Throwable var26) {
                     var4.addSuppressed(var26);
                  }
               } else {
                  var3.close();
               }
            }

         }
      } catch (Throwable var29) {
         var2 = var29;
         throw var29;
      } finally {
         if(var1 != null) {
            if(var2 != null) {
               try {
                  var1.close();
               } catch (Throwable var25) {
                  var2.addSuppressed(var25);
               }
            } else {
               var1.close();
            }
         }

      }

   }

   public void testMultiCatch() {
      try {
         Method var1 = this.getClass().getDeclaredMethod("foo", new Class[0]);
      } catch (SecurityException var2) {
         var2.printStackTrace();
      }

   }

   private void testSwitchString() {
      String var1 = "";
      byte var3 = -1;
      switch(var1.hashCode()) {
      case 2112:
         if(var1.equals("BB")) {
            var3 = 1;
         } else if(var1.equals("Aa")) {
            var3 = 0;
         }
         break;
      case 3040:
         if(var1.equals("__")) {
            var3 = 2;
         }
      }

      switch(var3) {
      case 0:
         System.out.println("!");
         break;
      case 1:
         System.out.println("?");
         break;
      case 2:
         System.out.println("_");
         break;
      default:
         System.out.println("#");
      }

   }

   public void testLambda() {
   }
}
