package org.jetbrains.protocolReader;

import gnu.trove.THashMap;
import gnu.trove.THashSet;

import java.util.*;

public class GlobalScope {
  private final State state;

  public GlobalScope(Collection<TypeHandler<?>> typeHandlers, Collection<GeneratedCodeMap> basePackages) {
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
    private final Collection<GeneratedCodeMap> basePackages;
    private final THashSet<TypeHandler<?>> typesWithFactories = new THashSet<>();
    private final List<TypeHandler<?>> typesWithFactoriesList = new ArrayList<>();

    State(Collection<TypeHandler<?>> typeHandlers, Collection<GeneratedCodeMap> basePackages) {
      this.basePackages = basePackages;
      typeToName = buildLocalTypeNameMap(typeHandlers);
    }

    String getTypeImplReference(TypeHandler<?> typeHandler) {
      String localName = typeToName.get(typeHandler);
      if (localName != null) {
        return localName;
      }

      for (GeneratedCodeMap base : basePackages) {
        String result = base.getTypeImplementationReference(typeHandler.getTypeClass());
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

    String getTypeImplShortName(TypeHandler<?> typeHandler) {
      String result = typeToName.get(typeHandler);
      if (result == null) {
        throw new RuntimeException();
      }
      return result;
    }

    private static Map<TypeHandler<?>, String> buildLocalTypeNameMap(Collection<TypeHandler<?>> typeHandlers) {
      List<TypeHandler<?>> list = new ArrayList<>(typeHandlers);
      // Sort to produce consistent GeneratedCodeMap later.
      Collections.sort(list, new Comparator<TypeHandler<?>>() {
        @Override
        public int compare(TypeHandler<?> o1, TypeHandler<?> o2) {
          return getName(o1).compareTo(getName(o2));
        }

        private String getName(TypeHandler<?> handler) {
          return handler.getTypeClass().getName();
        }
      });

      int uniqueCode = 0;
      Map<TypeHandler<?>, String> result = new THashMap<>(list.size());
      for (TypeHandler<?> handler : list) {
        String conflict = result.put(handler, Util.TYPE_NAME_PREFIX + Integer.toString(uniqueCode++));
        if (conflict != null) {
          throw new RuntimeException();
        }
      }
      return result;
    }
  }
}
