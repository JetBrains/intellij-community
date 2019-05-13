import java.util.List;

public class z<P> {
   private List<P> a;
   private Class<P> b;

   private z(Class<P> var1) {
      this.b = var1;
   }

   public static <P> z<P> a(Class<P> var0) {
      return new z(var0);
   }

   public List<P> a() {
      // $FF: Couldn't be decompiled
   }

   public List<P> b() {
      return t.b(this.b);
   }
}
