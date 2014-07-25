package org.jetbrains.protocolReader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jsonProtocol.ProtocolMetaModel;

class TypeData {
  private final String name;

  private Input input;
  private Output output;

  private ProtocolMetaModel.StandaloneType type;
  private StandaloneTypeBinding commonBinding;

  TypeData(String name) {
    this.name = name;
  }

  void setType(@NotNull ProtocolMetaModel.StandaloneType type) {
    this.type = type;
  }

  Input getInput() {
    if (input == null) {
      input = new Input();
    }
    return input;
  }

  Output getOutput() {
    if (output == null) {
      output = new Output();
    }
    return output;
  }

  TypeRef get(Direction direction) {
    return direction.get(this);
  }

  void checkComplete() {
    if (input != null) {
      input.checkResolved();
    }
    if (output != null) {
      output.checkResolved();
    }
  }

  enum Direction {
    INPUT() {
      @Override
      TypeRef get(TypeData typeData) {
        return typeData.getInput();
      }
    },
    OUTPUT() {
      @Override
      TypeRef get(TypeData typeData) {
        return typeData.getOutput();
      }
    };
    abstract TypeRef get(TypeData typeData);
  }

  abstract class TypeRef {
    private StandaloneTypeBinding oneDirectionBinding;

    BoxableType resolve(@NotNull TypeMap typeMap, @NotNull DomainGenerator domainGenerator) {
      if (commonBinding != null) {
        return commonBinding.getJavaType();
      }
      if (oneDirectionBinding != null) {
        return oneDirectionBinding.getJavaType();
      }
      StandaloneTypeBinding binding = resolveImpl(domainGenerator);
      if (binding == null) {
        return null;
      }

      if (binding.getDirection() == null) {
        commonBinding = binding;
      }
      else {
        oneDirectionBinding = binding;
      }
      typeMap.addTypeToGenerate(binding);
      return binding.getJavaType();
    }

    abstract StandaloneTypeBinding resolveImpl(DomainGenerator domainGenerator);

    void checkResolved() {
      if (type == null && !name.equals("int")) {
        throw new RuntimeException();
      }
    }
  }

  class Output extends TypeRef {
    @Override
    StandaloneTypeBinding resolveImpl(@NotNull DomainGenerator domainGenerator) {
      if (type == null) {
        if (name.equals("int")) {
          return new StandaloneTypeBinding() {
            @Override
            public BoxableType getJavaType() {
              return BoxableType.INT;
            }

            @Override
            public void generate() {
            }

            @Override
            public Direction getDirection() {
              return null;
            }
          };
        }

        throw new RuntimeException();
      }
      return domainGenerator.createStandaloneOutputTypeBinding(type, name);
    }
  }

  class Input extends TypeRef {
    @Override
    StandaloneTypeBinding resolveImpl(@NotNull DomainGenerator domainGenerator) {
      if (type == null) {
        throw new RuntimeException();
      }
      return domainGenerator.createStandaloneInputTypeBinding(type);
    }
  }
}
