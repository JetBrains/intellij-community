import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class d {
   private static List<a<?, ?>> a = new ArrayList();
   public static boolean b;

   public static List<a<?, ?>> a() {
      return a;
   }

   public static <K, V> a<K, V> a(String var0, int var1, f<K, V> var2, long var3, TimeUnit var5, g<V> var6, long var7, TimeUnit var9) {
      e var10 = new e(var0, var1, TimeUnit.MILLISECONDS.convert(var3, var5), var2, var6, TimeUnit.MILLISECONDS.convert(var7, var9));
      a.add(var10);
      return var10;
   }

   public static <K, V> a<K, V> a(String var0, int var1, long var2, TimeUnit var4) {
      e var5 = new e(var0, var1, TimeUnit.MILLISECONDS.convert(var2, var4), (f)null, (g)null, 10000L);
      a.add(var5);
      return var5;
   }

   public static void b() {
      boolean var2 = b;
      Iterator var0 = a().iterator();

      while(var0.hasNext()) {
         a var1 = (a)var0.next();
         var1.i();
         if (var2) {
            break;
         }
      }

   }
}
