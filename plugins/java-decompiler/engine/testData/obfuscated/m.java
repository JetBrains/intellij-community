import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class m<T> implements k<T> {
   private Set<T> a = new LinkedHashSet();

   public void a(T var1) {
      this.a.add(var1);
   }

   public void a(Collection<? extends T> var1) {
      this.a.addAll(var1);
   }

   public Set<T> a() {
      return this.a;
   }
}
