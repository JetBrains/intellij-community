public class y<P> {
   private P a;
   private Class<P> b;
   private boolean c;
   public static int d;

   private y(Class<P> var1) {
      this.b = var1;
   }

   public static <P> y<P> a(Class<P> var0) {
      return new y(var0);
   }

   public P a() {
      // $FF: Couldn't be decompiled
   }
}
