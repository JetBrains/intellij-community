// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.registerUProblem
import com.intellij.lang.jvm.actions.annotationRequest
import com.intellij.lang.jvm.actions.createAddAnnotationActions
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UFile
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

private const val PERSISTENT_STATE_COMPONENT_FQN = "com.intellij.openapi.components.PersistentStateComponent"
private const val BASE_STATE_FQN = "com.intellij.openapi.components.BaseState"
private const val STORED_PROPERTY_FQN = "com.intellij.openapi.components.StoredProperty"
private const val ATOMIC_REFERENCE_FQN = "java.util.concurrent.atomic.AtomicReference"
private const val XMLB_PROPERTY_FQN = "com.intellij.util.xmlb.annotations.Property"
private const val XMLB_XCOLLECTION_FQN = "com.intellij.util.xmlb.annotations.XCollection"
private const val XMLB_XMAP_FQN = "com.intellij.util.xmlb.annotations.XMap"
private const val XMLB_TRANSIENT_FQN = "com.intellij.util.xmlb.annotations.Transient"
private const val KOTLIN_TRANSIENT_FQN = "kotlin.jvm.Transient"
private const val KOTLINX_SERIALIZABLE_FQN = "kotlinx.serialization.Serializable"
private const val DELEGATE_SUFFIX = "\$delegate"

/**
 * The annotations that make the store keep a member.
 *
 * The list repeats `hasStoreAnnotations` of `com.intellij.util.xmlb.BeanBinding`.
 * `com.intellij.configurationStore.Property` is a different annotation, and the store ignores it here.
 */
private val STORE_ANNOTATIONS: List<String> = listOf(
  "com.intellij.util.xmlb.annotations.OptionTag",
  "com.intellij.util.xmlb.annotations.Tag",
  "com.intellij.util.xmlb.annotations.Attribute",
  XMLB_PROPERTY_FQN,
  "com.intellij.util.xmlb.annotations.Text",
  "com.intellij.util.xmlb.annotations.CollectionBean",
  "com.intellij.util.xmlb.annotations.MapAnnotation",
  XMLB_XMAP_FQN,
  XMLB_XCOLLECTION_FQN,
  "com.intellij.util.xmlb.annotations.AbstractCollection",
)

/**
 * The types that hold no setting.
 *
 * The store writes a value through reflection, and it cannot write a value of such a type. A member of
 * such a type is a dependency of the component, so the report must skip it. `ComponentManager` covers
 * `Project`, `Module` and `Application`, because each one extends it.
 */
private val INFRASTRUCTURE_TYPES: List<String> = listOf(
  "com.intellij.openapi.components.ComponentManager",
  "kotlinx.coroutines.CoroutineScope",
  "kotlinx.coroutines.flow.Flow",
)

/**
 * Reports a member of a state class that the store does not save.
 *
 * The store binds a member through a field or through a property accessor pair. It skips a final field
 * and a non-public field that has no store annotation. It also skips a property that has no setter.
 * Such a member takes its default value again after each IDE restart, and the user loses the setting.
 *
 * The report skips a class that carries `@kotlinx.serialization.Serializable`, because
 * `kotlinx.serialization` replaces the bean binding of such a class. That binding ignores every
 * xmlb annotation, so such a class needs `@kotlinx.serialization.Transient` to skip a member.
 */
internal class PersistentStatePropertyNotSerializedInspection : DevKitUastInspectionBase() {
  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!DevKitInspectionUtil.isClassAvailable(holder, PERSISTENT_STATE_COMPONENT_FQN)) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    // The state class of a component in this file. The visitor needs it, because a state class
    // that does not extend BaseState has no other mark.
    val stateTypeNames = lazy(LazyThreadSafetyMode.NONE) { collectStateTypeNames(holder.file) }
    return UastHintedVisitorAdapter.create(
      holder.file.language,
      object : AbstractUastNonRecursiveVisitor() {
        override fun visitClass(node: UClass): Boolean {
          if (isStateClass(node.javaPsi, stateTypeNames)) {
            checkStateClass(node, holder)
          }
          return true
        }
      },
      arrayOf(UClass::class.java),
    )
  }
}

private fun isStateClass(psiClass: PsiClass, stateTypeNames: Lazy<Set<String>>): Boolean {
  if (psiClass.isInterface || psiClass.isAnnotationType || psiClass.isEnum) return false
  // `kotlinx.serialization` replaces the bean binding for a class that carries `@Serializable`.
  // It saves a read-only property and a private property, so the rules below do not apply.
  if (hasAnnotation(psiClass, listOf(KOTLINX_SERIALIZABLE_FQN))) return false
  if (InheritanceUtil.isInheritor(psiClass, BASE_STATE_FQN)) return true
  val qualifiedName = psiClass.qualifiedName ?: return false
  return stateTypeNames.value.contains(qualifiedName)
}

/**
 * Returns the qualified name of each state class that a component in [file] declares.
 *
 * The search stays inside the file, so it does not use an index.
 */
private fun collectStateTypeNames(file: PsiFile): Set<String> {
  val uFile = file.toUElementOfType<UFile>() ?: return emptySet()
  val componentClass = JavaPsiFacade.getInstance(file.project).findClass(PERSISTENT_STATE_COMPONENT_FQN, file.resolveScope)
                       ?: return emptySet()
  val typeParameter = componentClass.typeParameters.firstOrNull() ?: return emptySet()
  val result = HashSet<String>()
  val queue = ArrayDeque(uFile.classes)
  while (queue.isNotEmpty()) {
    val uClass = queue.removeFirst()
    queue.addAll(uClass.innerClasses)
    val componentPsiClass = uClass.javaPsi
    if (!componentPsiClass.isInheritor(componentClass, true)) continue
    val substitutor = TypeConversionUtil.getClassSubstitutor(componentClass, componentPsiClass, PsiSubstitutor.EMPTY) ?: continue
    val stateClass = PsiUtil.resolveClassInClassTypeOnly(substitutor.substitute(typeParameter)) ?: continue
    stateClass.qualifiedName?.let { result.add(it) }
  }
  return result
}

private fun checkStateClass(uClass: UClass, holder: ProblemsHolder) {
  val model = StateSerializationModel(uClass.javaPsi)
  for (uField in uClass.fields) {
    if (uField.sourcePsi == null) continue
    val field = uField.javaPsi as? PsiField ?: continue
    val problem = model.findProblem(field) ?: continue
    val fixes = if (problem.hasAnnotationFix) {
      val actions = createAddAnnotationActions(field, annotationRequest(storeAnnotationFor(field.type)))
      IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), holder.file)
    }
    else {
      LocalQuickFix.EMPTY_ARRAY
    }
    holder.registerUProblem(uField, DevKitBundle.message(problem.messageKey, problem.propertyName), *fixes)
  }
}

private class FieldProblem(val messageKey: String, val propertyName: String, val hasAnnotationFix: Boolean)

/**
 * The set of names that the store binds for a state class.
 *
 * The class repeats `com.intellij.serialization.PropertyCollector` with the configuration of
 * `com.intellij.util.xmlb.BeanBinding`: `collectAccessors = true`, `collectPrivateFields = false`
 * and `collectFinalFields = false`.
 */
private class StateSerializationModel(stateClass: PsiClass) {
  /** The property names that the store binds. */
  private val boundNames = HashSet<String>()

  /** The names of a public getter, also of a getter that the store rejects. */
  private val publicGetterNames = HashSet<String>()

  /** The names of a public setter, also of a setter that the store rejects. */
  private val publicSetterNames = HashSet<String>()

  /** Each accessor of a name, at any visibility. The report uses it to find a `@Transient` mark. */
  private val declaredAccessors = HashMap<String, MutableList<PsiMethod>>()

  init {
    val hierarchy = serializableHierarchy(stateClass)
    collectPropertyAccessors(hierarchy)
    for (psiClass in hierarchy) {
      collectOwnFields(psiClass)
    }
  }

  fun findProblem(field: PsiField): FieldProblem? {
    if (field is PsiEnumConstant) return null
    if (field.hasModifierProperty(PsiModifier.STATIC) || field.hasModifierProperty(PsiModifier.TRANSIENT)) return null

    val fieldName = field.name
    val isDelegate = fieldName.endsWith(DELEGATE_SUFFIX)
    // A Kotlin delegate field holds the value only for a BaseState stored property. Skip `by lazy` and a custom delegate.
    if (isDelegate && !InheritanceUtil.isInheritor(field.type, STORED_PROPERTY_FQN)) return null
    // The store cannot write a dependency of the component, so the member holds no setting.
    if (INFRASTRUCTURE_TYPES.any { InheritanceUtil.isInheritor(field.type, it) }) return null
    // The store cannot create an instance of this type, so the member holds no setting.
    if (hasOnlyParameterizedConstructors(field.type)) return null

    val candidates = propertyNameCandidates(fieldName) ?: return null
    if (candidates.any { boundNames.contains(it) }) return null

    val hasPublicGetter = candidates.any { publicGetterNames.contains(it) }
    // A private member with no public getter and no stored property delegate is an implementation detail.
    if (field.hasModifierProperty(PsiModifier.PRIVATE) && !isDelegate && !hasPublicGetter) return null
    if (isExcludedOnPurpose(field, candidates)) return null

    val hasPublicSetter = candidates.any { publicSetterNames.contains(it) }
    val isReadOnly = (hasPublicGetter && !hasPublicSetter) ||
                     (field.hasModifierProperty(PsiModifier.PUBLIC) && field.hasModifierProperty(PsiModifier.FINAL))
    // A store annotation cannot bind a stored property delegate. The delegate field holds a `StoredProperty`
    // object, not the value, and the annotation does not apply to a delegated Kotlin property. Only a public
    // accessor pair binds such a property, so the report asks for that and offers no annotation fix.
    val messageKey = when {
      isDelegate && isReadOnly -> "inspection.persistent.state.property.not.serialized.delegate.read.only"
      isDelegate -> "inspection.persistent.state.property.not.serialized.delegate.not.public"
      isReadOnly -> "inspection.persistent.state.property.not.serialized.read.only"
      else -> "inspection.persistent.state.property.not.serialized.not.public"
    }
    return FieldProblem(messageKey, candidates.first(), hasAnnotationFix = !isDelegate)
  }

  /**
   * Returns true when the author excluded the member from the store on purpose.
   *
   * The store uses a stricter rule, so this check only removes noise from the report.
   */
  private fun isExcludedOnPurpose(field: PsiField, candidates: List<String>): Boolean {
    return isTransient(field) || accessorsOf(candidates).any { isTransient(it) }
  }

  private fun accessorsOf(candidates: List<String>): List<PsiMethod> = candidates.flatMap { declaredAccessors[it].orEmpty() }

  private fun serializableHierarchy(stateClass: PsiClass): List<PsiClass> {
    val result = ArrayList<PsiClass>()
    val visited = HashSet<PsiClass>()
    var current: PsiClass? = stateClass
    while (current != null && visited.add(current)) {
      result.add(current)
      val superClass = current.superClass ?: break
      val qualifiedName = superClass.qualifiedName
      if (qualifiedName == CommonClassNames.JAVA_LANG_OBJECT || qualifiedName == ATOMIC_REFERENCE_FQN || isTransient(superClass)) break
      current = superClass
    }
    return result
  }

  private fun collectPropertyAccessors(hierarchy: List<PsiClass>) {
    val getters = HashMap<String, PsiMethod>()
    val setters = HashMap<String, PsiMethod>()
    for (psiClass in hierarchy) {
      for (method in psiClass.methods) {
        if (method.isConstructor) continue
        val propertyData = getPropertyData(method.name) ?: continue
        if (propertyData.name == "class") continue
        if (method.parameterList.parametersCount != (if (propertyData.isSetter) 1 else 0)) continue
        declaredAccessors.computeIfAbsent(propertyData.name) { ArrayList() }.add(method)
        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) continue
        // The most derived class wins, as in the store.
        val candidates = if (propertyData.isSetter) setters else getters
        candidates.putIfAbsent(propertyData.name, method)
      }
    }
    publicGetterNames.addAll(getters.keys)
    publicSetterNames.addAll(setters.keys)
    for ((name, getter) in getters) {
      if (isAcceptableProperty(getter, setters[name])) {
        boundNames.add(name)
      }
    }
  }

  private fun isAcceptableProperty(getter: PsiMethod, setter: PsiMethod?): Boolean {
    if (isTransient(getter)) return false
    val getterType = getter.returnType ?: return false
    if (setter == null) {
      return isCollectionOrMap(getterType) && hasStoreAnnotation(getter)
    }
    if (isTransient(setter)) return false
    val setterType = setter.parameterList.parameters.firstOrNull()?.type ?: return false
    // The store compares the erased types, because it uses reflection.
    return TypeConversionUtil.erasure(getterType) == TypeConversionUtil.erasure(setterType)
  }

  private fun collectOwnFields(psiClass: PsiClass) {
    for (field in psiClass.fields) {
      if (isCollectedField(field)) {
        boundNames.add(field.name)
      }
    }
  }

  private fun isCollectedField(field: PsiField): Boolean {
    if (field is PsiEnumConstant) return false
    if (field.hasModifierProperty(PsiModifier.STATIC) || field.hasModifierProperty(PsiModifier.TRANSIENT)) return false
    if (hasStoreAnnotation(field)) return true
    if (!field.hasModifierProperty(PsiModifier.PUBLIC)) return false
    if (field.hasModifierProperty(PsiModifier.FINAL) && !isCollectionOrMap(field.type)) return false
    return !isTransient(field)
  }
}

/**
 * Returns the store names of a field, or null when the store never binds the field.
 *
 * A Kotlin property adds a second name, because the store takes the name from the accessor.
 * The accessors of `isEnabled` are `isEnabled` and `setEnabled`, so the store name is `enabled`.
 */
private fun propertyNameCandidates(fieldName: String): List<String>? {
  // A Kotlin delegate field keeps the name of the property, and it adds the `$delegate` suffix.
  val propertyName = fieldName.removeSuffix(DELEGATE_SUFFIX)
  // A synthetic member of the compiler, for example a `Companion` reference or an inline class marker.
  if (propertyName.isEmpty() || propertyName.contains('$')) return null
  val result = ArrayList<String>(2)
  result.add(propertyName)
  if (propertyName.length > 2 && propertyName.startsWith("is") && propertyName[2].isUpperCase()) {
    result.add(decapitalize(propertyName.substring(2)))
  }
  return result
}

/** Repeats `getPropertyData` of `com.intellij.serialization.PropertyCollector`. */
private fun getPropertyData(methodName: String): PropertyData? {
  var part = ""
  var isSetter = false
  when {
    methodName.startsWith("get") -> part = methodName.substring(3)
    methodName.startsWith("is") -> part = methodName.substring(2)
    methodName.startsWith("set") -> {
      part = methodName.substring(3)
      isSetter = true
    }
  }
  if (part.isEmpty()) return null
  val suffixIndex = part.indexOf('$')
  if (suffixIndex > 0) {
    // Ignore a special Kotlin property. An internal member keeps the name before the suffix.
    if (part.endsWith("\$annotations")) return null
    part = part.substring(0, suffixIndex)
  }
  else if (suffixIndex == 0) {
    return null
  }
  return PropertyData(decapitalize(part), isSetter)
}

private class PropertyData(val name: String, val isSetter: Boolean)

/** Repeats `decapitalize` of `com.intellij.serialization.PropertyCollector`. */
private fun decapitalize(name: String): String {
  if (name.isEmpty() || (name.length > 1 && name[1].isUpperCase() && name[0].isUpperCase())) return name
  return name[0].lowercaseChar() + name.substring(1)
}

private fun hasStoreAnnotation(owner: PsiModifierListOwner): Boolean = hasAnnotation(owner, STORE_ANNOTATIONS)

private fun isTransient(owner: PsiModifierListOwner): Boolean = hasAnnotation(owner, listOf(XMLB_TRANSIENT_FQN, KOTLIN_TRANSIENT_FQN))

private fun hasAnnotation(owner: PsiModifierListOwner, qualifiedNames: List<String>): Boolean {
  val annotations = owner.modifierList?.annotations ?: return false
  return annotations.any { annotation -> qualifiedNames.any { annotation.hasQualifiedName(it) } }
}

private fun isCollectionOrMap(type: PsiType): Boolean {
  return InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_COLLECTION) ||
         InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)
}

/**
 * Returns the store annotation that fits [type].
 *
 * `@XMap` and `@XCollection` state the shape of the XML for a map and for a collection, and the store
 * documentation asks for them. `@Property` is the general annotation, and it fits every other type.
 */
private fun storeAnnotationFor(type: PsiType): String {
  return when {
    InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP) -> XMLB_XMAP_FQN
    InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_COLLECTION) -> XMLB_XCOLLECTION_FQN
    else -> XMLB_PROPERTY_FQN
  }
}

/**
 * Returns true when the store can never create an instance of [type].
 *
 * The store instantiates a bean with its no-arg constructor, `beanClass.getDeclaredConstructor()`.
 * A class that declares a constructor, but none of them takes no parameter, has no such
 * constructor. This check reads the constructors of the light class, because Kotlin synthesizes
 * a no-arg constructor when every constructor parameter has a default value.
 */
private fun hasOnlyParameterizedConstructors(type: PsiType): Boolean {
  val psiClass = (type as? PsiClassType)?.resolve() ?: return false
  if (psiClass.isEnum || psiClass.qualifiedName == CommonClassNames.JAVA_LANG_STRING || isCollectionOrMap(type)) return false
  val constructors = psiClass.constructors
  return constructors.isNotEmpty() && constructors.none { it.parameterList.isEmpty }
}
