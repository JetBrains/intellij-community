import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class Collectors2 {
  static <T> Map<T, Integer> combine(Collection<Map<? extends T, Integer>> pMaps) {
    return pMaps.stream()
      .map((java.util.function.Function<Map<? extends T, Integer>, java.util.Set<? extends Map.Entry<? extends T, Integer>>>) (integerMap) -> integerMap.entrySet())
      .flatMap(Collection::stream)
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                                (pEntry1, pEntry2) -> pEntry1 + pEntry2,
                                (Supplier<HashMap<T, Integer>>) HashMap::new
               )
      );
  }
}