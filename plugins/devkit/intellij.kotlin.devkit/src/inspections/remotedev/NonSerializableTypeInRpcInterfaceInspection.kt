// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.remotedev

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle.message
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*

/**
 * The inspection checks that all parameters and return types of RPC interface methods are serializable.
 * It checks generic type arguments (all types in `A<B, C>`) and referenced type properties, recursively.
 * In some complex cases when a deeply present type is not serializable, it may be unclear at first sight what is
 * not serializable, so the exception messages contains a type path, which help understand where the non-serializable
 * type is used.
 */
internal class NonSerializableTypeInRpcInterfaceInspection : LocalInspectionTool() {

  private val rpcAnnotationFqn = FqName("fleet.rpc.Rpc")
  private val serializableAnnotationFqn = FqName("kotlinx.serialization.Serializable")
  private val transientAnnotationFqn = FqName("kotlinx.serialization.Transient")
  private val kSerializedClassId = ClassId.fromString("kotlinx/serialization/KSerializer")

  private val defaultSerializableTypes = arrayOf(
    //<editor-fold desc="primitives, arrays, collections, etc.">
    // primitives:
    "kotlin.Boolean",
    "kotlin.Byte",
    "kotlin.Char",
    "kotlin.Double",
    "kotlin.Float",
    "kotlin.Int",
    "kotlin.Long",
    "kotlin.Short",

    // unsigned primitives:
    "kotlin.UInt",
    "kotlin.ULong",
    "kotlin.UByte",
    "kotlin.UShort",

    // common Kotlin types (handled by built-in Kotlin serializers):
    "kotlin.String",
    "kotlin.Pair",
    "kotlin.Triple",
    "kotlin.Unit",
    "kotlin.time.Duration",

    // collections:
    "kotlin.collections.Collection",
    "kotlin.collections.List",
    "kotlin.collections.Set",
    "kotlin.collections.Map",
    "kotlin.Array",

    // primitive type arrays:
    "kotlin.BooleanArray",
    "kotlin.ByteArray",
    "kotlin.CharArray",
    "kotlin.DoubleArray",
    "kotlin.FloatArray",
    "kotlin.IntArray",
    "kotlin.LongArray",
    "kotlin.ShortArray",

    // unsigned primitive type arrays:
    "kotlin.UByteArray",
    "kotlin.UIntArray",
    "kotlin.ULongArray",
    "kotlin.UShortArray",

    // handled by RPC out-of-the-box:
    "kotlinx.coroutines.flow.Flow",
    "kotlinx.coroutines.channels.ReceiveChannel",
    "kotlinx.coroutines.channels.SendChannel",
    "kotlinx.coroutines.Deferred"
    //</editor-fold>
  )
    .map { FqName(it) }
    .toSet()

  /**
   *  Services accessible through RPC (supported only in Fleet).
   */
  private val fleetRemoteServiceTypes = arrayOf(
    "fleet.rpc.core.RemoteResource",
    "fleet.rpc.core.RemoteObject",
    "fleet.util.async.Resource"
  )
    .map { ClassId.topLevel(FqName(it)) }
    .toSet()

  /**
   * Types that we don't want to report now (their support should be implemented later).
   */
  private val ignoredTypes = setOf(
    // related to Rhizome entities; unclear how to handle them
    FqName("fleet.kernel.DurableRef")
  )

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!DevKitInspectionUtil.isAllowed(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
    return object : KtVisitorVoid() {

      override fun visitNamedFunction(function: KtNamedFunction) {
        analyze(function) {
          val functionSymbol = function.symbol as? KaNamedFunctionSymbol ?: return@analyze
          val containingInterface = (functionSymbol.containingDeclaration as? KaClassSymbol) ?: return@analyze
          if (!containingInterface.isRpcInterface()) return@analyze

          val typesToCheck: List<KtTypeReference> =
            (function.valueParameters.map { it.typeReference } + function.typeReference).filterNotNull()
          for (typeReference in typesToCheck) {
            checkType(holder, typeReference)
          }
        }
      }

      private fun KaClassSymbol.isRpcInterface(): Boolean {
        return classKind == KaClassKind.INTERFACE &&
               annotations.any { annotation -> annotation.getFqn() == rpcAnnotationFqn }
      }

      private fun checkType(holder: ProblemsHolder, typeReference: KtTypeReference) {
        analyze(typeReference) {
          val type = typeReference.type as? KaClassType ?: return@analyze
          if (typeReference.containingFile.isInFleetSources() && fleetRemoteServiceTypes.any { type.isSubtypeOf(it) }) return
          val nonSerializableProperty = findNonSerializableProperty(type, typePath = mutableListOf(type))
          if (nonSerializableProperty != null) {
            val (propertyPath, nonSerializableTypePath) = nonSerializableProperty
            val message = if (propertyPath.isEmpty()) {
              val typeShortTextPath = nonSerializableTypePath.map { it.getShortText() }
              if (typeShortTextPath.size > 1) {
                message(
                  "inspection.remote.dev.rpc.non.serializable.type.with.chain",
                  typeShortTextPath[0], typeShortTextPath.joinToString(" → ")
                )
              } else {
                message("inspection.remote.dev.rpc.non.serializable.type", nonSerializableTypePath[0])
              }
            }
            else {
              val propertyPathString = propertyPath.joinToString(".")
              val typeShortTextPath = nonSerializableTypePath.map { it.getShortText() }.toMutableList()
              val nonSerializableType = typeShortTextPath.removeLast()
              val nonSerializablePropertyContainerType = typeShortTextPath.last()
              if (typeShortTextPath.size > 1) {
                message(
                  "inspection.remote.dev.rpc.non.serializable.property.with.chain",
                  nonSerializablePropertyContainerType, propertyPathString, nonSerializableType, typeShortTextPath.joinToString(" → ")
                )
              } else {
                message(
                  "inspection.remote.dev.rpc.non.serializable.property",
                  nonSerializablePropertyContainerType, propertyPathString, nonSerializableType
                )
              }
            }
            holder.registerProblem(typeReference, message)
          }
        }
      }

      private fun PsiFile.isInFleetSources(): Boolean {
        return virtualFile?.path?.contains("/fleet/") == true
      }

      /**
       * @param visited store types to avoid infinite recursion in types holding a reference to the same type;
       *                we don't need semantical equality comparison here, as probably it's better to check a type twice
       *                than perform an expensive type equality operation
       */
      private fun KaSession.findNonSerializableProperty(
        type: KaClassType?,
        propertyPath: MutableList<String> = mutableListOf(),
        typePath: MutableList<KaClassType>,
        visited: MutableSet<KaType> = mutableSetOf(),
      ): Pair<List<String>, List<KaClassType>>? {
        if (type == null) return null
        val typeSymbol = type.symbol
        if (typeSymbol.getFqn() in ignoredTypes) return null
        if (!visited.add(type)) return null

        // check type and its arguments
        val typeOrTypeArgumentNonSerializableProperty = findNonSerializablePropertyInTypeOrArguments(type, propertyPath, typePath, visited)
        if (typeOrTypeArgumentNonSerializableProperty != null) {
          return typeOrTypeArgumentNonSerializableProperty
        }
        // check type properties
        else {
          if (typeSymbol.getFqn() in defaultSerializableTypes) return null // do not check default serializable type properties
          if (typeSymbol.hasCustomSerializer()) return null // expect that custom serializers handle type serialization fully

          for (member in getPotentiallySerializableMembers(type)) {
            check(member is KtCallableDeclaration) // should be parameter or property

            val memberName = member.name ?: continue
            if (member.isAnnotatedWith(serializableAnnotationFqn)) continue
            if (member.isAbstractPropertyOfNonFinalType()) continue
            val memberType = member.returnType as? KaClassType ?: continue
            propertyPath += memberName
            typePath += memberType
            val nonSerializableProperty = findNonSerializableProperty(memberType, propertyPath, typePath, visited)
            if (nonSerializableProperty != null) {
              return nonSerializableProperty
            }
            propertyPath.removeLast()
            typePath.removeLast()
          }
          return null
        }
      }

      private fun KaClassLikeSymbol.hasCustomSerializer(): Boolean {
        val serializableAnnotation = annotations.find { it.classId?.asSingleFqName() == serializableAnnotationFqn } ?: return false
        return serializableAnnotation.arguments.any { it.name.asString() == "with" }
      }

      /**
       * From Fleet RPC doc:
       *
       * Currently, the compiler plugin supports serialization of:
       * 1. Types with `@Serializable` annotation (every type that has `.serializer()` generated by `kotlinx.serialization` compiler plugin).
       * 2. Types with serializers provided by `kotlinx.serialization` in `kotlinx.serialization.builtins`.
       * 3. `SendChannel`, `ReceiveChannel`, `Flow`, `Deferred` (corresponding serializers are in `fleet.rpc.core.SerializationKt`).
       */
      private fun KaSession.findNonSerializablePropertyInTypeOrArguments(
        type: KaClassType?,
        propertyPath: MutableList<String>,
        typePath: MutableList<KaClassType>,
        visited: MutableSet<KaType>,
      ): Pair<List<String>, List<KaClassType>>? {
        if (type == null) return null // * in A<*> for generics
        if (type.isEnum()) return null // do not report enums until IJPL-190471 is clarified
        val typeSymbol = type.symbol
        if (typeSymbol.getFqn() in ignoredTypes) return null // do not report ignored types
        if (typeSymbol.hasCustomSerializer()) return null
        val typeName = typeSymbol.getFqn() ?: return null
        if (typeName !in defaultSerializableTypes && !hasSerializer(typeSymbol)) {
          return propertyPath to typePath
        }

        for (arg in type.typeArguments) {
          if (arg.type is KaTypeParameterType) {
            // 1. Ignore unknown generic types at the top (RPC interface) level.
            // 2. Serializable types should be provided on a higher level if a nested property is checked.
            continue
          }
          val argClassType = arg.type as? KaClassType ?: continue
          if (argClassType.symbol.hasCustomSerializer()) continue
          typePath += argClassType
          // reset propertyPath, because we want property path to be bound to a single type:
          val nonSerializableProperty = findNonSerializableProperty(argClassType, typePath = typePath, visited = visited)
          if (nonSerializableProperty != null) {
            return nonSerializableProperty
          }
          typePath.removeLast()
        }
        return null
      }

      private fun KaClassType.isEnum(): Boolean {
        val classSymbol = this.symbol
        return classSymbol is KaClassSymbol && classSymbol.classKind == KaClassKind.ENUM_CLASS
      }

      /**
       * Abstract properties of non-final types can be overridden with a serializable type, so ignore checking them (too complex to support).).
       */
      private fun KtDeclaration.isAbstractPropertyOfNonFinalType(): Boolean {
        val declaration = this
        analyze(declaration) {
          val declarationSymbol = declaration.symbol
          if (declarationSymbol.modality == KaSymbolModality.ABSTRACT) {
            val type = (declarationSymbol as? KaPropertySymbol)?.returnType ?: return false
            val typeSymbol = type.symbol ?: return false
            return typeSymbol.modality != KaSymbolModality.FINAL
          }
        }
        return false
      }

      private fun KaSession.hasSerializer(typeSymbol: KaClassLikeSymbol?): Boolean {
        val namedClassSymbol = typeSymbol as? KaNamedClassSymbol ?: return false
        val serializerFunctionContainer = when (namedClassSymbol.classKind) {
                                            KaClassKind.OBJECT -> namedClassSymbol
                                            else -> namedClassSymbol.companionObject
                                          } ?: return false
        return serializerFunctionContainer.declaredMemberScope.declarations.any { isKotlinSerializationFunction(it) }
      }

      private fun KaSession.isKotlinSerializationFunction(symbol: KaDeclarationSymbol): Boolean {
        return symbol is KaNamedFunctionSymbol &&
               symbol.name.asString() == "serializer" &&
               symbol.returnType.isClassType(kSerializedClassId)
      }

      private fun KtDeclaration.isAnnotatedWith(fqn: FqName): Boolean {
        val declaration = this
        return analyze(declaration) {
          // Kotlin code is destructured to many different Ka objects, depending on whether something is a simple property,
          // uses an explicit backing field, is a primary constructor parameter, and so on. There is no single API to get annotations
          // of KtDeclaration.
          val annotatedSymbol = when (val declarationSymbol = declaration.symbol) {
                                  is KaPropertySymbol -> {
                                    if (declaration is KtProperty && usesFieldKeyword(declaration)) declarationSymbol
                                    else declarationSymbol.backingFieldSymbol
                                  }
                                  is KaValueParameterSymbol -> declarationSymbol.generatedPrimaryConstructorProperty
                                  else -> declarationSymbol
                                } ?: return false
          annotatedSymbol.annotations.any { it.getFqn() == fqn }
        }
      }

      private fun getPotentiallySerializableMembers(type: KaType): List<KtDeclaration> {
        val typeSymbol = type.symbol ?: return emptyList()
        val ktClass = typeSymbol.psi as? KtClassOrObject ?: return emptyList()
        val memberDeclarations = ktClass.declarations.filter { it.isPotentiallySerializableProperty() }
        val primaryConstructorParameters = ktClass.primaryConstructor?.valueParameters?.filter { it.hasValOrVar() } ?: emptyList()
        return (memberDeclarations + primaryConstructorParameters).filter { !it.isAnnotatedWith(transientAnnotationFqn) }
      }

      private fun KtDeclaration.isPotentiallySerializableProperty(): Boolean {
        val property = this as? KtProperty ?: return false
        if (property.initializer != null) return true
        return usesFieldKeyword(property)
      }

      private fun usesFieldKeyword(property: KtProperty): Boolean {
        val get = property.getter
        val set = property.setter
        if (get == null && set == null) return true
        return usesFieldKeyword(get) || usesFieldKeyword(set)
      }

      // not perfect, but should work for standard cases, and non-standard false positives can be suppressed
      private fun usesFieldKeyword(accessor: KtPropertyAccessor?): Boolean {
        accessor?.bodyExpression?.let { body ->
          fun findFieldInElement(element: KtElement): Boolean {
            if (element is KtNameReferenceExpression && element.getReferencedName() == "field") {
              return true
            }
            return element.children.any { it is KtElement && findFieldInElement(it) }
          }
          return findFieldInElement(body)
        }
        return false
      }
    }
  }
}

private fun KaClassLikeSymbol.getFqn(): FqName? {
  return this.classId?.asSingleFqName()
}

private fun KaAnnotation.getFqn(): FqName? {
  return this.classId?.asSingleFqName()
}

private val nonTypeNameCharacters = arrayOf(' ', ',', '<', '>').toCharArray()

private fun KaClassType.getShortText(): String {
  val fullTextWithoutAliased = this.toString()
    .substringAfter('{').substringBefore("=}") // render only the visible type name, without alias information
  val result = StringBuilder()
  var i = 0
  while (i < fullTextWithoutAliased.length) {
    when (val c = fullTextWithoutAliased[i]) {
      in nonTypeNameCharacters -> {
        result.append(c)
        i++
      }
      else -> {
        val nameEnd = fullTextWithoutAliased.indexOfAny(nonTypeNameCharacters, i).takeIf { it >= 0 } ?: fullTextWithoutAliased.length
        val fullName = fullTextWithoutAliased.substring(i, nameEnd)
        result.append(fullName.substringAfterLast('/'))
        i = nameEnd
      }
    }
  }
  return result.toString()
}
