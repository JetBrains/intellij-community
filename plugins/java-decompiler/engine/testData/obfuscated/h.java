import java.util.concurrent.atomic.AtomicLong;

public class h {
   private final AtomicLong a = new AtomicLong();

   public long a() {
      boolean var5 = n.c;
      long var1 = this.a.incrementAndGet();

      long var10000;
      label48: {
         try {
            var10000 = var1;
            if (var5) {
               return var10000;
            }

            if (var1 <= 9223372036854775797L) {
               break label48;
            }
         } catch (a_ var8) {
            throw var8;
         }

         AtomicLong var3;
         synchronized(var3 = this.a) {
            AtomicLong var9 = this.a;
            if (!var5) {
               try {
                  if (var9.get() > 9223372036854775797L) {
                     this.a.set(0L);
                  }
               } catch (a_ var6) {
                  throw var6;
               }

               var9 = var3;
            }

         }
      }

      var10000 = var1;
      return var10000;
   }

   public String b() {
      return String.valueOf(this.a());
   }
}
