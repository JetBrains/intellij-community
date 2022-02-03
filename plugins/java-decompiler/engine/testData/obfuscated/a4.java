import java.text.DateFormat;
import java.util.Date;
import java.util.logging.Level;

@aa(
   a = {ac.class}
)
public class a4 implements ac {
   private static boolean a;
   private static y<ad> b;
   private static final String[] c;

   public static void main(String[] var0) throws Exception {
      t.a.setLevel(Level.FINE);
      t.a();
      a = true;

      while(true) {
         Thread.sleep(10000L);
         System.out.println(c[1] + ((ad)b.a()).a());
      }
   }

   public void a() throws Exception {
      try {
         if (a) {
            System.out.println(c[0] + DateFormat.getTimeInstance().format(new Date()));
         }

      } catch (Exception var1) {
         throw var1;
      }
   }

   static {
      String[] var10000;
      int var1;
      int var2;
      char[] var10003;
      char[] var10004;
      char[] var4;
      int var10005;
      int var10006;
      char var10007;
      byte var10008;
      label51: {
         var10000 = new String[2];
         var10003 = "f\u0019\u0016n-[\u001c\u0016n0AKS".toCharArray();
         var10005 = var10003.length;
         var1 = 0;
         var10004 = var10003;
         var2 = var10005;
         if (var10005 <= 1) {
            var4 = var10003;
            var10006 = var1;
         } else {
            var10004 = var10003;
            var2 = var10005;
            if (var10005 <= var1) {
               break label51;
            }

            var4 = var10003;
            var10006 = var1;
         }

         while(true) {
            var10007 = var4[var10006];
            switch (var1 % 5) {
               case 0:
                  var10008 = 50;
                  break;
               case 1:
                  var10008 = 113;
                  break;
               case 2:
                  var10008 = 115;
                  break;
               case 3:
                  var10008 = 78;
                  break;
               default:
                  var10008 = 89;
            }

            var4[var10006] = (char)(var10007 ^ var10008);
            ++var1;
            if (var2 == 0) {
               var10006 = var2;
               var4 = var10004;
            } else {
               if (var2 <= var1) {
                  break;
               }

               var4 = var10004;
               var10006 = var1;
            }
         }
      }

      var10000[0] = (new String(var10004)).intern();
      var10003 = "~\u0010\u0000:y[\u001f\u0005!:S\u0005\u001a!7\bQ".toCharArray();
      var10005 = var10003.length;
      var1 = 0;
      var10004 = var10003;
      var2 = var10005;
      if (var10005 <= 1) {
         var4 = var10003;
         var10006 = var1;
      } else {
         var10004 = var10003;
         var2 = var10005;
         if (var10005 <= var1) {
            var10000[1] = (new String(var10003)).intern();
            c = var10000;
            a = false;
            b = y.a(ad.class);
            return;
         }

         var4 = var10003;
         var10006 = var1;
      }

      while(true) {
         var10007 = var4[var10006];
         switch (var1 % 5) {
            case 0:
               var10008 = 50;
               break;
            case 1:
               var10008 = 113;
               break;
            case 2:
               var10008 = 115;
               break;
            case 3:
               var10008 = 78;
               break;
            default:
               var10008 = 89;
         }

         var4[var10006] = (char)(var10007 ^ var10008);
         ++var1;
         if (var2 == 0) {
            var10006 = var2;
            var4 = var10004;
         } else {
            if (var2 <= var1) {
               var10000[1] = (new String(var10004)).intern();
               c = var10000;
               a = false;
               b = y.a(ad.class);
               return;
            }

            var4 = var10004;
            var10006 = var1;
         }
      }
   }
}
