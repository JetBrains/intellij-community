package org.jetbrains.protocolReader;

import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class GlobalScope {
  private final State state;

  public GlobalScope(Collection<TypeHandler<?>> typeHandlers, Collection<Map<Class<?>, String>> basePackages) {
    state = new State(typeHandlers, basePackages);
  }

  public GlobalScope(GlobalScope globalScope) {
    state = globalScope.state;
  }

  public String getTypeImplReference(TypeHandler<?> typeHandler) {
    return state.getTypeImplReference(typeHandler);
  }

  public String requireFactoryGenerationAndGetName(TypeHandler<?> typeHandler) {
    return state.requireFactoryGenerationAndGetName(typeHandler);
  }

  public String getTypeImplShortName(TypeHandler<?> typeHandler) {
    return state.getTypeImplShortName(typeHandler);
  }

  public FileScope newFileScope(StringBuilder output) {
    return new FileScope(this, output);
  }

  public List<TypeHandler<?>> getTypeFactories() {
    return state.typesWithFactoriesList;
  }

  private static class State {
    private final Map<TypeHandler<?>, String> typeToName;
    private final Collection<Map<Class<?>, String>> basePackages;
    private final THashSet<TypeHandler<?>> typesWithFactories = new THashSet<>();
    private final List<TypeHandler<?>> typesWithFactoriesList = new ArrayList<>();

    State(Collection<TypeHandler<?>> typeHandlers, Collection<Map<Class<?>, String>> basePackages) {
      this.basePackages = basePackages;

      int uniqueCode = 0;
      Map<TypeHandler<?>, String> result = new THashMap<>(typeHandlers.size());
      for (TypeHandler<?> handler : typeHandlers) {
        String conflict = result.put(handler, Util.TYPE_NAME_PREFIX + Integer.toString(uniqueCode++));
        if (conflict != null) {
          throw new RuntimeException();
        }
      }
      typeToName = result;
    }

    String getTypeImplReference(TypeHandler<?> typeHandler) {
      String localName = typeToName.get(typeHandler);
      if (localName != null) {
        return localName;
      }

      for (Map<Class<?>, String> base : basePackages) {
        String result = base.get(typeHandler.getTypeClass());
        if (result != null) {
          return result;
        }
      }

      throw new RuntimeException();
    }

    public String requireFactoryGenerationAndGetName(TypeHandler<?> typeHandler) {
      String name = getTypeImplShortName(typeHandler);
      if (typesWithFactories.add(typeHandler)) {
        typesWithFactoriesList.add(typeHandler);
      }
      return name;
    }

    @NotNull
    String getTypeImplShortName(TypeHandler<?> typeHandler) {
      return typeToName.get(typeHandler);
    }
  }
}
