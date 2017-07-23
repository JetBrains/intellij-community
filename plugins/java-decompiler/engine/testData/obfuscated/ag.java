public class ag {
   private long a = 0L;
   private long[] b = new long[100];
   private int c = 0;
   private int d = 0;

   public void a(long param1) {
      // $FF: Couldn't be decompiled
   }

   public double a() {
      try {
         if (this.d == 0) {
            return 0.0D;
         }
      } catch (a_ var4) {
         throw var4;
      }

      double var1 = 0.0D;

      for(int var3 = 0; var3 <= this.d; ++var3) {
         var1 += (double)this.b[var3];
      }

      return var1 / (double)this.d;
   }

   public long b() {
      return this.a;
   }
}
