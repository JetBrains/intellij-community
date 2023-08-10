import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class n<F, S> {
   private F a;
   private S b;
   public static boolean c;
   private static final String d;

   public n() {
   }

   public n(F var1) {
      this.a = var1;
   }

   public n(F var1, S var2) {
      this.a = var1;
      this.b = var2;
   }

   public F a() {
      return this.a;
   }

   public void a(F var1) {
      this.a = var1;
   }

   public S b() {
      return this.b;
   }

   public void b(S var1) {
      this.b = var1;
   }

   public boolean equals(Object param1) {
      // $FF: Couldn't be decompiled
   }

   private boolean a(Object param1, Object param2) {
      // $FF: Couldn't be decompiled
   }

   public String toString() {
      return this.a + d + this.b;
   }

   public int hashCode() {
      Object var10000;
      label28: {
         try {
            if (this.a == null) {
               var10000 = "";
               break label28;
            }
         } catch (a_ var2) {
            throw var2;
         }

         var10000 = this.a;
      }

      Object var10001;
      int var3;
      try {
         var3 = var10000.hashCode() / 2;
         if (this.b == null) {
            var10001 = "";
            return var3 + var10001.hashCode() / 2;
         }
      } catch (a_ var1) {
         throw var1;
      }

      var10001 = this.b;
      return var3 + var10001.hashCode() / 2;
   }

   public static <T extends n<K, V>, K, V> List<K> a(Collection<T> var0) {
      ArrayList var1 = new ArrayList(var0.size());
      Iterator var2 = var0.iterator();

      while(var2.hasNext()) {
         n var3 = (n)var2.next();
         var1.add(var3.a());
      }

      return var1;
   }

   public static <T extends n<K, V>, K, V> List<V> b(Collection<T> param0) {
      // $FF: Couldn't be decompiled
   }

   public static <K, V> List<n<K, V>> a(Map<K, V> var0) {
      boolean var4 = c;
      ArrayList var1 = new ArrayList(var0.size());
      Iterator var2 = var0.entrySet().iterator();

      ArrayList var10000;
      while(true) {
         if (var2.hasNext()) {
            Map.Entry var3 = (Map.Entry)var2.next();

            try {
               var10000 = var1;
               if (var4) {
                  break;
               }

               var1.add(new n(var3.getKey(), var3.getValue()));
               if (!var4) {
                  continue;
               }
            } catch (a_ var6) {
               throw var6;
            }

            int var5 = ap.c;
            ++var5;
            ap.c = var5;
         }

         var10000 = var1;
         break;
      }

      return var10000;
   }

   public static <K, V> Map<K, V> c(Collection<n<K, V>> var0) {
      HashMap var1 = new HashMap();
      Iterator var2 = var0.iterator();

      while(var2.hasNext()) {
         n var3 = (n)var2.next();
         var1.put(var3.a(), var3.b());
      }

      return var1;
   }

   static {
      char[] var10000 = "p\u001c".toCharArray();
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
         switch (var1 % 5) {
            case 0:
               var10005 = 74;
               break;
            case 1:
               var10005 = 60;
               break;
            case 2:
               var10005 = 116;
               break;
            case 3:
               var10005 = 28;
               break;
            default:
               var10005 = 38;
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
