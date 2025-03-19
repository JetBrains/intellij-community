import java.util.Collection;

public abstract class i<T> implements k<T> {
   public void a(Collection<? extends T> var1) {
      boolean var4 = n.c;

      for(Object var3 : var1) {
         this.a(var3);
         if (var4) {
            break;
         }
      }

   }
}
