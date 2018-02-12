import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ab implements u {
   private List<Object> a = new ArrayList();

   public void a(Class<?> param1) throws Exception {
      // $FF: Couldn't be decompiled
   }

   public void a() throws Exception {
      int var3 = y.d;
      Iterator var1 = this.a.iterator();

      while(true) {
         if (var1.hasNext()) {
            Object var2 = var1.next();

            try {
               v.a(var2);
               if (var3 != 0) {
                  break;
               }

               if (var3 == 0) {
                  continue;
               }
            } catch (Exception var5) {
               throw var5;
            }

            int var4 = ap.c;
            ++var4;
            ap.c = var4;
         }

         this.a.clear();
         break;
      }

   }
}
