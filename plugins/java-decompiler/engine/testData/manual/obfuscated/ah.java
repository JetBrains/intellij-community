import java.util.concurrent.TimeUnit;

public class ah {
   private volatile long a = -1L;
   private volatile long b = 0L;

   public void a() {
      // $FF: Couldn't be decompiled
   }

   public double a(TimeUnit var1) {
      return (double)(this.b / this.b(var1));
   }

   public long b() {
      return this.b;
   }

   public long b(TimeUnit var1) {
      long var2 = System.currentTimeMillis() - this.a;
      return TimeUnit.MILLISECONDS.convert(var2, var1);
   }

   public void c() {
      this.a = System.currentTimeMillis();
      this.b = 0L;
   }
}
