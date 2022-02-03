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
         var10003 = "\b[\u0006\u001d\u001b\u0000D\r\u001a\u0016(y\u0002\u001c\t\u000bL\n\u0003\u0007)".toCharArray();
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
      var10003 = ".A\u0002\u001c\u0011".toCharArray();
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
            b = var10000;
            return;
         }

         var4 = var10003;
         var10006 = var1;
      }

      while(true) {
         var10007 = var4[var10006];
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

         var4[var10006] = (char)(var10007 ^ var10008);
         ++var1;
         if (var2 == 0) {
            var10006 = var2;
            var4 = var10004;
         } else {
            if (var2 <= var1) {
               var10000[1] = (new String(var10004)).intern();
               b = var10000;
               return;
            }

            var4 = var10004;
            var10006 = var1;
         }
      }
   }
}
