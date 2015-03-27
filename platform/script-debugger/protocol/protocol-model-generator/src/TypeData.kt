package org.jetbrains.protocolModelGenerator

import org.jetbrains.jsonProtocol.ProtocolMetaModel

class TypeData(private val name: String) {
  private var input: Input? = null
  private var output: Output? = null

  private var type: ProtocolMetaModel.StandaloneType? = null
  private var commonBinding: StandaloneTypeBinding? = null

  fun setType(type: ProtocolMetaModel.StandaloneType) {
    this.type = type
  }

  fun getInput(): Input {
    if (input == null) {
      input = Input()
    }
    return input!!
  }

  fun getOutput(): Output {
    if (output == null) {
      output = Output()
    }
    return output!!
  }

  fun get(direction: Direction): TypeRef {
    return direction.get(this)
  }

  fun checkComplete() {
    if (input != null) {
      input!!.checkResolved()
    }
    if (output != null) {
      output!!.checkResolved()
    }
  }

  abstract  class Direction {
    companion object {
      val INPUT = object : Direction() {
        override fun get(typeData: TypeData)  = typeData.getInput()
      }

      val OUTPUT = object : Direction() {
        override fun get(typeData: TypeData) = typeData.getOutput()
      }
    }

    abstract fun get(typeData: TypeData): TypeRef
  }

  abstract inner class TypeRef {
    private var oneDirectionBinding: StandaloneTypeBinding? = null

    fun resolve(typeMap: TypeMap, domainGenerator: DomainGenerator): BoxableType? {
      if (commonBinding != null) {
        return commonBinding!!.getJavaType()
      }
      if (oneDirectionBinding != null) {
        return oneDirectionBinding!!.getJavaType()
      }
      val binding = resolveImpl(domainGenerator)
      if (binding == null) {
        return null
      }

      if (binding.getDirection() == null) {
        commonBinding = binding
      }
      else {
        oneDirectionBinding = binding
      }
      typeMap.addTypeToGenerate(binding)
      return binding.getJavaType()
    }

    abstract fun resolveImpl(domainGenerator: DomainGenerator): StandaloneTypeBinding?

    fun checkResolved() {
      if (type == null && !(name == "int" || name == "any")) {
        throw RuntimeException()
      }
    }
  }

  inner class Output : TypeRef() {
    override fun resolveImpl(domainGenerator: DomainGenerator): StandaloneTypeBinding? {
      if (type == null) {
        if (name == "int") {
          return object : StandaloneTypeBinding {
            override fun getJavaType(): BoxableType {
              return BoxableType.INT
            }

            override fun generate() {
            }

            override fun getDirection(): Direction? {
              return null
            }
          }
        }
        else if (name == "any") {
          return ANY
        }

        throw RuntimeException()
      }
      return domainGenerator.createStandaloneOutputTypeBinding(type!!, name)
    }
  }

  inner class Input : TypeRef() {
    override fun resolveImpl(domainGenerator: DomainGenerator): StandaloneTypeBinding? {
      if (type == null) {
        if (name == "int") {
          return object : StandaloneTypeBinding {
            override fun getJavaType(): BoxableType {
              return BoxableType.INT
            }

            override fun generate() {
            }

            override fun getDirection(): Direction? {
              return null
            }
          }
        }
        else if (name == "any") {
          return ANY
        }

        throw RuntimeException()
      }
      return domainGenerator.createStandaloneInputTypeBinding(type!!)
    }
  }
}

public val ANY: StandaloneTypeBinding = object : StandaloneTypeBinding {
  override fun getJavaType(): BoxableType {
    return BoxableType.ANY_STRING
  }

  override fun generate() {
  }

  override fun getDirection(): TypeData.Direction? {
    return null
  }
}
