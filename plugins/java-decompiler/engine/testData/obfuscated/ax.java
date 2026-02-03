import java.util.concurrent.TimeUnit;

public class ax {
   private long a = 0L;
   private static final String[] b;

   public static ax a() {
      return new ax();
   }

   private ax() {
      this.b();
   }

   public void b() {
      this.a = System.nanoTime();
   }

   public long c() {
      return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - this.a);
   }

   public String d() {
      return this.c() + b[0];
   }

   public String a(boolean var1) {
      String var2 = String.valueOf(((double)System.nanoTime() - (double)this.a) / 1000.0);

      try {
         if (var1) {
            this.b();
         }

         return var2;
      } catch (a_ var3) {
         throw var3;
      }
   }

   public String e() {
      String var1 = this.c() + b[1];
      this.b();
      return var1;
   }

   static {
      char[] var17;
      String[] var10000;
      label51: {
         var10000 = new String[2];
         char[] var10003 = "}|".toCharArray();
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
                  var10008 = 16;
                  break;
               case 1:
                  var10008 = 15;
                  break;
               case 2:
                  var10008 = 44;
                  break;
               case 3:
                  var10008 = 84;
                  break;
               default:
                  var10008 = 86;
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
      char[] var9 = "}|".toCharArray();
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
               var48 = 16;
               break;
            case 1:
               var48 = 15;
               break;
            case 2:
               var48 = 44;
               break;
            case 3:
               var48 = 84;
               break;
            default:
               var48 = 86;
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
