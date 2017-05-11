public abstract class ap implements ak {
   private final String a;
   private final String b;
   public static int c;
   private static final String d;

   public ap(String var1, String var2) {
      this.b = var1;
      this.a = var2;
   }

   public String b() {
      return this.a;
   }

   public String a() {
      return this.b;
   }

   public int hashCode() {
      return (this.b + this.a).hashCode();
   }

   public boolean equals(Object param1) {
      // $FF: Couldn't be decompiled
   }

   public String toString() {
      return this.b + d + this.a;
   }

   static {
      char[] var10000 = "K\u000eX".toCharArray();
      int var10002 = var10000.length;
      int var1 = 0;
      char[] var10001 = var10000;
      int var2 = var10002;
      int var10003;
      char[] var4;
      if (var10002 <= 1) {
         var4 = var10000;
         var10003 = var1;
      } else {
         var10001 = var10000;
         var2 = var10002;
         if (var10002 <= var1) {
            d = (new String(var10000)).intern();
            return;
         }

         var4 = var10000;
         var10003 = var1;
      }

      while(true) {
         char var10004 = var4[var10003];
         byte var10005;
         switch(var1 % 5) {
         case 0:
            var10005 = 107;
            break;
         case 1:
            var10005 = 35;
            break;
         case 2:
            var10005 = 120;
            break;
         case 3:
            var10005 = 65;
            break;
         default:
            var10005 = 23;
         }

         var4[var10003] = (char)(var10004 ^ var10005);
         ++var1;
         if (var2 == 0) {
            var10003 = var2;
            var4 = var10001;
         } else {
            if (var2 <= var1) {
               d = (new String(var10001)).intern();
               return;
            }

            var4 = var10001;
            var10003 = var1;
         }
      }
   }
}
