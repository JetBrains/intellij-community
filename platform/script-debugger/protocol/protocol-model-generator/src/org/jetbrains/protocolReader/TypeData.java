package org.jetbrains.protocolReader;

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

  void setType(ProtocolMetaModel.StandaloneType type) {
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

    BoxableType resolve(TypeMap typeMap, DomainGenerator domainGenerator) {
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
      if (type == null) {
        throw new RuntimeException();
      }
    }
  }

  class Output extends TypeRef {
    @Override
    StandaloneTypeBinding resolveImpl(final DomainGenerator domainGenerator) {
      if (type == null) {
        throw new RuntimeException();
      }
      return domainGenerator.createStandaloneOutputTypeBinding(type, name);
    }
  }

  class Input extends TypeRef {
    @Override
    StandaloneTypeBinding resolveImpl(DomainGenerator domainGenerator) {
      if (type == null) {
        throw new RuntimeException();
      }
      return domainGenerator.createStandaloneInputTypeBinding(type);
    }
  }
}
