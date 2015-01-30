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

  public GlobalScope(Collection<TypeWriter<?>> typeWriters, Collection<Map<Class<?>, String>> basePackages) {
    state = new State(typeWriters, basePackages);
  }

  public GlobalScope(GlobalScope globalScope) {
    state = globalScope.state;
  }

  public String getTypeImplReference(TypeWriter<?> typeWriter) {
    return state.getTypeImplReference(typeWriter);
  }

  public String requireFactoryGenerationAndGetName(TypeWriter<?> typeWriter) {
    return state.requireFactoryGenerationAndGetName(typeWriter);
  }

  public String getTypeImplShortName(TypeWriter<?> typeWriter) {
    return state.getTypeImplShortName(typeWriter);
  }

  public FileScope newFileScope(StringBuilder output) {
    return new FileScope(this, output);
  }

  public List<TypeWriter<?>> getTypeFactories() {
    return state.typesWithFactoriesList;
  }

  private static class State {
    private final Map<TypeWriter<?>, String> typeToName;
    private final Collection<Map<Class<?>, String>> basePackages;
    private final THashSet<TypeWriter<?>> typesWithFactories = new THashSet<>();
    private final List<TypeWriter<?>> typesWithFactoriesList = new ArrayList<>();

    State(Collection<TypeWriter<?>> typeWriters, Collection<Map<Class<?>, String>> basePackages) {
      this.basePackages = basePackages;

      int uniqueCode = 0;
      Map<TypeWriter<?>, String> result = new THashMap<>(typeWriters.size());
      for (TypeWriter<?> handler : typeWriters) {
        String conflict = result.put(handler, Util.TYPE_NAME_PREFIX + Integer.toString(uniqueCode++));
        if (conflict != null) {
          throw new RuntimeException();
        }
      }
      typeToName = result;
    }

    String getTypeImplReference(TypeWriter<?> typeWriter) {
      String localName = typeToName.get(typeWriter);
      if (localName != null) {
        return localName;
      }

      for (Map<Class<?>, String> base : basePackages) {
        String result = base.get(typeWriter.typeClass);
        if (result != null) {
          return result;
        }
      }

      throw new RuntimeException();
    }

    public String requireFactoryGenerationAndGetName(TypeWriter<?> typeWriter) {
      String name = getTypeImplShortName(typeWriter);
      if (typesWithFactories.add(typeWriter)) {
        typesWithFactoriesList.add(typeWriter);
      }
      return name;
    }

    @NotNull
    String getTypeImplShortName(TypeWriter<?> typeWriter) {
      return typeToName.get(typeWriter);
    }
  }
}
