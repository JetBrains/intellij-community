package org.jetbrains.protocolModelGenerator

import org.jetbrains.jsonProtocol.ProtocolMetaModel
import kotlin.properties.Delegates

val ANY = object : StandaloneTypeBinding {
  override fun getJavaType() = BoxableType.ANY_STRING

  override fun generate() {
  }

  override fun getDirection() = null
}

class TypeData(private val name: String) {
  val input: Input by Delegates.lazy {
    Input()
  }

  val output: Output by Delegates.lazy {
    Output()
  }

  private var type: ProtocolMetaModel.StandaloneType? = null
  private var commonBinding: StandaloneTypeBinding? = null

  fun setType(type: ProtocolMetaModel.StandaloneType) {
    this.type = type
  }

  abstract class Direction {
    companion object {
      val INPUT = object : Direction() {
        override fun get(typeData: TypeData) = typeData.input
      }

      val OUTPUT = object : Direction() {
        override fun get(typeData: TypeData) = typeData.output
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