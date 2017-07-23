import java.util.Collection;
import java.util.Iterator;

public abstract class i<T> implements k<T> {
   public void a(Collection<? extends T> var1) {
      boolean var4 = n.c;
      Iterator var2 = var1.iterator();

      while(var2.hasNext()) {
         Object var3 = var2.next();
         this.a(var3);
         if (var4) {
            break;
         }
      }

   }
}
