import java.util.Date;

public class b<K, V> {
   protected ah a;
   protected long b;
   protected long c;
   protected final K d;
   protected V e;
   protected long f;
   protected long g;

   public ah a() {
      return this.a;
   }

   public long b() {
      return this.a.b();
   }

   public Date c() {
      return new Date(this.b);
   }

   public void a(long var1) {
      this.b = var1;
   }

   public Date d() {
      return new Date(this.c);
   }

   public Date e() {
      return new Date(this.f);
   }

   public void b(long var1) {
      this.c = var1;
   }

   public V f() {
      return this.e;
   }

   public void a(V var1) {
      this.e = var1;
   }

   public b(K var1, V var2, long var3, long var5) {
      boolean var7 = d.b;
      super();
      this.a = new ah();
      this.b = 0L;
      this.c = 0L;
      this.d = var1;
      this.f = var3;
      this.g = var5;
      this.c = System.currentTimeMillis();
      this.b = this.c;
      this.e = var2;
      if (ap.c != 0) {
         boolean var10000;
         label18: {
            try {
               if (var7) {
                  var10000 = false;
                  break label18;
               }
            } catch (a_ var8) {
               throw var8;
            }

            var10000 = true;
         }

         d.b = var10000;
      }

   }

   public long g() {
      return this.f;
   }

   public void c(long var1) {
      this.f = var1;
   }

   public long h() {
      return this.g;
   }

   public void d(long var1) {
      this.g = var1;
   }

   public K i() {
      return this.d;
   }
}
