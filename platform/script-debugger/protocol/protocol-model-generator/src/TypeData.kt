package org.jetbrains.protocolModelGenerator

import org.jetbrains.jsonProtocol.ProtocolMetaModel

internal val ANY = object : StandaloneTypeBinding {
  override fun getJavaType() = BoxableType.ANY_STRING

  override fun generate() {
  }

  override fun getDirection() = null
}

internal class TypeData(private val name: String) {
  val input by lazy { Input() }

  val output by lazy { Output() }

  internal var type: ProtocolMetaModel.StandaloneType? = null

  private var commonBinding: StandaloneTypeBinding? = null

  enum class Direction {
    INPUT {
      override fun get(typeData: TypeData)  = typeData.input
    },
    OUTPUT {
      override fun get(typeData: TypeData) = typeData.output
    };

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
      val binding = resolveImpl(domainGenerator) ?: return null

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
  }

  inner class Output : TypeRef() {
    override fun resolveImpl(domainGenerator: DomainGenerator): StandaloneTypeBinding? {
      if (type == null) {
        if (name == "int") {
          return object : StandaloneTypeBinding {
            override fun getJavaType() = BoxableType.INT

            override fun generate() {
            }

            override fun getDirection() = null
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
            override fun getJavaType() = BoxableType.INT

            override fun generate() {
            }

            override fun getDirection() = null
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