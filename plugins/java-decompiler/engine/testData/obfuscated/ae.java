import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.locks.ReentrantLock;

@aa(
   a = {ad.class}
)
public class ae implements ad {
   @x(
      a = ac.class
   )
   private List<ac> a;
   private long b = 0L;
   private Timer c;
   private ReentrantLock d = new ReentrantLock();
   public static boolean e;

   public ae() {
      this.a();
   }

   public void a() {
      // $FF: Couldn't be decompiled
   }

   public void b() {
      // $FF: Couldn't be decompiled
   }

   public String a() {
      try {
         if (this.b == 0L) {
            return "-";
         }
      } catch (a_ var1) {
         throw var1;
      }

      return DateFormat.getDateTimeInstance().format(new Date(this.b));
   }

   static List a(ae var0) {
      return var0.a;
   }

   static long a(ae var0, long var1) {
      return var0.b = var1;
   }
}
