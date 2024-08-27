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
      char[] var17;
      String[] var10000;
      label51: {
         var10000 = new String[2];
         char[] var10003 = "f\u0019\u0016n-[\u001c\u0016n0AKS".toCharArray();
         int var10005 = var10003.length;
         int var1 = 0;
         var17 = var10003;
         int var5 = var10005;
         char[] var29;
         int var10006;
         if (var10005 <= 1) {
            var29 = var10003;
            var10006 = var1;
         } else {
            var17 = var10003;
            var5 = var10005;
            if (var10005 <= var1) {
               break label51;
            }

            var29 = var10003;
            var10006 = var1;
         }

         while(true) {
            char var10007 = var29[var10006];
            byte var10008;
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

            var29[var10006] = (char)(var10007 ^ var10008);
            ++var1;
            if (var5 == 0) {
               var10006 = var5;
               var29 = var17;
            } else {
               if (var5 <= var1) {
                  break;
               }

               var29 = var17;
               var10006 = var1;
            }
         }
      }

      var10000[0] = (new String(var17)).intern();
      char[] var9 = "~\u0010\u0000:y[\u001f\u0005!:S\u0005\u001a!7\bQ".toCharArray();
      int var36 = var9.length;
      int var2 = 0;
      var17 = var9;
      int var12 = var36;
      char[] var39;
      int var46;
      if (var36 <= 1) {
         var39 = var9;
         var46 = var2;
      } else {
         var17 = var9;
         var12 = var36;
         if (var36 <= var2) {
            var10000[1] = (new String(var9)).intern();
            c = var10000;
            a = false;
            b = y.a(ad.class);
            return;
         }

         var39 = var9;
         var46 = var2;
      }

      while(true) {
         char var47 = var39[var46];
         byte var48;
         switch (var2 % 5) {
            case 0:
               var48 = 50;
               break;
            case 1:
               var48 = 113;
               break;
            case 2:
               var48 = 115;
               break;
            case 3:
               var48 = 78;
               break;
            default:
               var48 = 89;
         }

         var39[var46] = (char)(var47 ^ var48);
         ++var2;
         if (var12 == 0) {
            var46 = var12;
            var39 = var17;
         } else {
            if (var12 <= var2) {
               var10000[1] = (new String(var17)).intern();
               c = var10000;
               a = false;
               b = y.a(ad.class);
               return;
            }

            var39 = var17;
            var46 = var2;
         }
      }
   }
}
