import java.util.Iterator;
import java.util.TimerTask;

class ba extends TimerTask {
   final ae a;
   private static final String[] b;

   private ba(ae var1) {
      this.a = var1;
   }

   public void run() {
      boolean var4 = ae.e;
      Iterator var1 = ae.a(this.a).iterator();

      while(true) {
         if (var1.hasNext()) {
            ac var2 = (ac)var1.next();

            label23: {
               try {
                  var2.a();
               } catch (Exception var6) {
                  r.a(b[0]).a(b[1], var2.getClass().getName()).a();
                  break label23;
               }

               if (var4) {
                  break;
               }
            }

            if (!var4) {
               continue;
            }

            int var5 = ap.c;
            ++var5;
            ap.c = var5;
         }

         ae.a(this.a, System.currentTimeMillis());
         break;
      }

   }

   ba(ae var1, af var2) {
      this(var1);
   }

   static {
      char[] var17;
      String[] var10000;
      label51: {
         var10000 = new String[2];
         char[] var10003 = "\b[\u0006\u001d\u001b\u0000D\r\u001a\u0016(y\u0002\u001c\t\u000bL\n\u0003\u0007)".toCharArray();
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
                  var10008 = 77;
                  break;
               case 1:
                  var10008 = 45;
                  break;
               case 2:
                  var10008 = 99;
                  break;
               case 3:
                  var10008 = 111;
                  break;
               default:
                  var10008 = 98;
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
      char[] var9 = ".A\u0002\u001c\u0011".toCharArray();
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
            b = var10000;
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
               var48 = 77;
               break;
            case 1:
               var48 = 45;
               break;
            case 2:
               var48 = 99;
               break;
            case 3:
               var48 = 111;
               break;
            default:
               var48 = 98;
         }

         var39[var46] = (char)(var47 ^ var48);
         ++var2;
         if (var12 == 0) {
            var46 = var12;
            var39 = var17;
         } else {
            if (var12 <= var2) {
               var10000[1] = (new String(var17)).intern();
               b = var10000;
               return;
            }

            var39 = var17;
            var46 = var2;
         }
      }
   }
}
