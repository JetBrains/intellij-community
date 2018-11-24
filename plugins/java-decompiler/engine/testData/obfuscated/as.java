import java.util.concurrent.TimeUnit;

public class as extends ap {
   private volatile long d = 0L;
   private volatile long e;
   private final TimeUnit f;
   private final Double g;
   private String h;
   private double i;

   public as(String var1, String var2, TimeUnit var3, Double var4, String var5) {
      super(var1, var2);
      this.f = var3;
      this.h = var5;
      this.g = var4;
      this.e = System.currentTimeMillis();
   }

   public String c() {
      return this.h;
   }

   public void a() {
      // $FF: Couldn't be decompiled
   }

   public void a(long var1) {
      try {
         if (9223372036854775797L - var1 > this.d) {
            this.d += var1;
         }

      } catch (a_ var3) {
         throw var3;
      }
   }

   public double d() {
      // $FF: Couldn't be decompiled
   }

   public double b() {
      return this.i;
   }

   public Double e() {
      return this.g;
   }
}
