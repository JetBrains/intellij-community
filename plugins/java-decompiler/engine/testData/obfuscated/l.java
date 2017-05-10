import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class l<T> implements k<T> {
   private List<T> a = new ArrayList();

   public void a(T var1) {
      this.a.add(var1);
   }

   public void a(Collection<? extends T> var1) {
      this.a.addAll(var1);
   }

   public List<T> a() {
      return this.a;
   }
}
