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
      String var2 = String.valueOf(((double)System.nanoTime() - (double)this.a) / 1000.0D);

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
         var10003 = "}|".toCharArray();
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
            switch(var1 % 5) {
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
      var10003 = "}|".toCharArray();
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
         switch(var1 % 5) {
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
