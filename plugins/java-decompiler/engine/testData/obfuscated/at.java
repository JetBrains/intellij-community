class at extends ap {
   final an d;
   private static final String e;

   at(an var1, String var2, String var3) {
      super(var2, var3);
      this.d = var1;
   }

   public double d() {
      return (double)an.a(this.d).getHeapMemoryUsage().getUsed() / 1024.0 / 1024.0;
   }

   public String c() {
      return e;
   }

   public Double e() {
      return (double)an.a(this.d).getHeapMemoryUsage().getMax() / 1024.0 / 1024.0;
   }

   static {
      char[] var10000 = "$b".toCharArray();
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
            e = (new String(var10000)).intern();
            return;
         }

         var4 = var10000;
         var10003 = var1;
      }

      while(true) {
         char var10004 = var4[var10003];
         byte var10005;
         switch (var1 % 5) {
            case 0:
               var10005 = 105;
               break;
            case 1:
               var10005 = 32;
               break;
            case 2:
               var10005 = 18;
               break;
            case 3:
               var10005 = 31;
               break;
            default:
               var10005 = 120;
         }

         var4[var10003] = (char)(var10004 ^ var10005);
         ++var1;
         if (var2 == 0) {
            var10003 = var2;
            var4 = var10001;
         } else {
            if (var2 <= var1) {
               e = (new String(var10001)).intern();
               return;
            }

            var4 = var10001;
            var10003 = var1;
         }
      }
   }
}
