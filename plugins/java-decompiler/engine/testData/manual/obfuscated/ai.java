import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@aa(
   a = {ac.class, ai.class}
)
public class ai implements ac {
   @x(
      a = am.class
   )
   private List<am> a;
   private static Map<ak, al> b = Collections.synchronizedMap(new LinkedHashMap());

   public void a() throws Exception {
      boolean var3 = an.k;

      for(am var2 : this.a) {
         var2.a(new j(this));
         if (var3) {
            break;
         }
      }

   }

   public Collection<al> a() {
      return b.values();
   }

   static Map b() {
      return b;
   }
}
