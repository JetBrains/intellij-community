import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections.map.LRUMap;

class e<K, V> implements a<K, V> {
   private static final int a = 25;
   protected int b;
   protected f<K, V> c;
   protected Map<K, b<K, V>> d;
   protected ah e = new ah();
   protected ah f = new ah();
   protected List<Long> g = new ArrayList(25);
   protected List<Long> h = new ArrayList(25);
   protected Date i = null;
   protected final String j;
   protected final long k;
   protected final g<V> l;
   private final long m;

   public e(String var1, int var2, long var3, f<K, V> var5, g<V> var6, long var7) {
      this.j = var1;
      this.b = var2;
      this.m = var7;
      if (var2 > 0) {
         this.d = Collections.synchronizedMap(new LRUMap(var2));
      } else {
         this.d = Collections.synchronizedMap(new HashMap(var2));
      }

      this.k = var3;
      this.c = var5;
      this.l = var6;
   }

   public String a() {
      return this.j;
   }

   public int b() {
      return this.b;
   }

   public int c() {
      return this.d.size();
   }

   public long d() {
      return this.e.b() + this.f.b();
   }

   public Long f() {
      long var1 = this.e.b();
      long var3 = this.f.b();

      long var10000;
      try {
         if (var1 + var3 == 0L) {
            var10000 = 0L;
            return var10000;
         }
      } catch (a_ var5) {
         throw var5;
      }

      var10000 = Math.round(100.0D * (double)var1 / (double)(var1 + var3));
      return var10000;
   }

   public Date h() {
      return this.i;
   }

   public void i() {
      // $FF: Couldn't be decompiled
   }

   public void j() {
      this.d.clear();
      this.f.c();
      this.e.c();
      this.i = new Date();
   }

   public V a(K var1) {
      return this.a(var1, this.c);
   }

   public boolean c(K var1) {
      return this.d.containsKey(var1);
   }

   public V a(K param1, f<K, V> param2) {
      // $FF: Couldn't be decompiled
   }

   public void a(K var1, V var2) {
      b var10000;
      b var10001;
      Object var10002;
      Object var10003;
      long var10004;
      label16: {
         try {
            var10000 = new b;
            var10001 = var10000;
            var10002 = var1;
            var10003 = var2;
            if (this.k > 0L) {
               var10004 = this.k + System.currentTimeMillis();
               break label16;
            }
         } catch (a_ var4) {
            throw var4;
         }

         var10004 = 0L;
      }

      var10001.<init>(var10002, var10003, var10004, this.m + System.currentTimeMillis());
      b var3 = var10000;
      this.d.put(var1, var3);
   }

   public void b(K var1) {
      this.d.remove(var1);
   }

   public Iterator<K> k() {
      return this.d.keySet().iterator();
   }

   public List<b<K, V>> l() {
      return new ArrayList(this.d.values());
   }

   public List<Long> e() {
      return this.g;
   }

   public List<Long> g() {
      return this.h;
   }
}
