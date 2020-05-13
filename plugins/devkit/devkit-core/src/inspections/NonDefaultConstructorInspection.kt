// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.jvm.JvmClassKind
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameterList
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.util.SmartList
import gnu.trove.THashSet
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.dom.ExtensionPoint
import org.jetbrains.idea.devkit.dom.ExtensionPoint.Area
import org.jetbrains.idea.devkit.util.processExtensionDeclarations
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.convertOpt

private const val serviceBeanFqn = "com.intellij.openapi.components.ServiceDescriptor"

class NonDefaultConstructorInspection : DevKitUastInspectionBase(UClass::class.java) {
  override fun checkClass(aClass: UClass, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    val javaPsi = aClass.javaPsi
    // Groovy from test data - ignore it
    if (javaPsi.language.id == "Groovy" || javaPsi.classKind != JvmClassKind.CLASS ||
        PsiUtil.isInnerClass(javaPsi) || PsiUtil.isLocalOrAnonymousClass(javaPsi) || PsiUtil.isAbstractClass(javaPsi) ||
        javaPsi.hasModifierProperty(PsiModifier.PRIVATE) /* ignore private classes */) {
      return null
    }

    val constructors = javaPsi.constructors
    // very fast path - do nothing if no constructors
    if (constructors.isEmpty()) {
      return null
    }

    val area: Area?
    val isService: Boolean
    // hack, allow Project-level @Service
    var isServiceAnnotation = false
    var extensionPoint: ExtensionPoint? = null
    if (javaPsi.hasAnnotation("com.intellij.openapi.components.Service")) {
      area = null
      isService = true
      isServiceAnnotation = true
    }
    else {
      // fast path - check by qualified name
      if (!isExtensionBean(aClass)) {
        // slow path - check using index
        extensionPoint = findExtensionPoint(aClass, manager.project) ?: return null
      }
      else if (javaPsi.name == "VcsConfigurableEP") {
        // VcsConfigurableEP extends ConfigurableEP but used directly, for now just ignore it as hardcoded exclusion
        return null
      }

      area = getArea(extensionPoint)
      isService = extensionPoint?.beanClass?.stringValue == serviceBeanFqn
    }

    val isAppLevelExtensionPoint = area == null || area == Area.IDEA_APPLICATION

    var errors: MutableList<ProblemDescriptor>? = null
    loop@ for (method in constructors) {
      if (isAllowedParameters(method.parameterList, extensionPoint, isAppLevelExtensionPoint, isServiceAnnotation)) {
        // allow to have empty constructor and extra (e.g. DartQuickAssistIntention)
        return null
      }

      if (errors == null) {
        errors = SmartList()
      }

      // kotlin is not physical, but here only physical is expected, so, convert to uast element and use sourcePsi
      val anchorElement = when {
        method.isPhysical -> method.identifyingElement!!
        else -> aClass.sourcePsi?.let { UastFacade.findPlugin(it)?.convertOpt<UMethod>(method, aClass)?.sourcePsi } ?: continue@loop
      }


      val kind = if (isService) "Service" else "Extension"
      val suffix = if (area == null) {
        " (except Project or Module if requested on corresponding level)"
      }
      else {
        if (isAppLevelExtensionPoint) "" else " (except ${if (area == Area.IDEA_PROJECT) "Project" else "Module"})"
      }
      errors.add(manager.createProblemDescriptor(anchorElement,
                                                 "$kind should not have constructor with parameters${suffix}.\nDo not instantiate services in constructor because they should be requested only when needed.", true,
                                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly))
    }
    return errors?.toTypedArray()
  }

  private fun getArea(extensionPoint: ExtensionPoint?): Area {
    val areaName = (extensionPoint ?: return Area.IDEA_APPLICATION).area.stringValue
    when (areaName) {
      "IDEA_PROJECT" -> return Area.IDEA_PROJECT
      "IDEA_MODULE" -> return Area.IDEA_MODULE
      else -> {
        when (extensionPoint.name.value) {
          "projectService" -> return Area.IDEA_PROJECT
          "moduleService" -> return Area.IDEA_MODULE
        }
      }
    }
    return Area.IDEA_APPLICATION
  }
}

private fun findExtensionPoint(clazz: UClass, project: Project): ExtensionPoint? {
  val parentClass = clazz.uastParent as? UClass
  if (parentClass == null) {
    val qualifiedName = clazz.qualifiedName ?: return null
    return findExtensionPointByImplementationClass(qualifiedName, qualifiedName, project)
  }
  else {
    val parentQualifiedName = parentClass.qualifiedName ?: return null
    // parent$inner string cannot be found, so, search by parent FQN
    return findExtensionPointByImplementationClass(parentQualifiedName, "$parentQualifiedName$${clazz.javaPsi.name}", project)
  }
}

private fun findExtensionPointByImplementationClass(searchString: String, qualifiedName: String, project: Project): ExtensionPoint? {
  var result: ExtensionPoint? = null
  val strictMatch = searchString === qualifiedName
  processExtensionDeclarations(searchString, project, strictMatch = strictMatch) { extension, tag ->
    val point = extension.extensionPoint ?: return@processExtensionDeclarations true
    when (point.beanClass.stringValue) {
      null -> {
        if (tag.attributes.any { it.name == Extension.IMPLEMENTATION_ATTRIBUTE && it.value == qualifiedName }) {
          result = point
          return@processExtensionDeclarations false
        }
      }
      serviceBeanFqn -> {
        if (tag.attributes.any { it.name == "serviceImplementation" && it.value == qualifiedName }) {
          result = point
          return@processExtensionDeclarations false
        }
      }
      else -> {
        // bean EP
        if (tag.name == "className" || tag.subTags.any { it.name == "className" && (strictMatch || it.textMatches(qualifiedName)) } || checkAttributes(tag, qualifiedName)) {
          result = point
          return@processExtensionDeclarations false
        }
      }
    }
    true
  }
  return result
}

// todo can we use attribute `with`?
private val ignoredTagNames = THashSet(listOf("semContributor", "modelFacade", "scriptGenerator", "editorActionHandler", "editorTypedHandler", "dataImporter", "java.error.fix", "explainPlanProvider"))

// problem - tag
//<lang.elementManipulator forClass="com.intellij.psi.css.impl.CssTokenImpl"
//                         implementationClass="com.intellij.psi.css.impl.CssTokenImpl$Manipulator"/>
// will be found for `com.intellij.psi.css.impl.CssTokenImpl`, but we need to ignore `forClass` and check that we have exact match for implementation attribute
private fun checkAttributes(tag: XmlTag, qualifiedName: String): Boolean {
  if (ignoredTagNames.contains(tag.name)) {
    // DbmsExtension passes Dbms instance directly, doesn't need to check
    return false
  }

  return tag.attributes.any {
    val name = it.name
    (name.startsWith(Extension.IMPLEMENTATION_ATTRIBUTE) || name == "instance") && it.value == qualifiedName
  }
}

private val allowedServiceQualifiedNames = setOf(
  "com.intellij.openapi.project.Project",
  "com.intellij.openapi.module.Module",
  "com.intellij.util.messages.MessageBus",
  "com.intellij.openapi.options.SchemeManagerFactory",
  "com.intellij.openapi.editor.actionSystem.TypedActionHandler",
  "com.intellij.database.Dbms"
)
private val allowedServiceNames = allowedServiceQualifiedNames.map { StringUtil.getShortName(it) }

private fun isAllowedParameters(list: PsiParameterList,
                                extensionPoint: ExtensionPoint?,
                                isAppLevelExtensionPoint: Boolean,
                                isServiceAnnotation: Boolean): Boolean {
  if (list.isEmpty) {
    return true
  }

  // hardcoded for now, later will be generalized
  if (!isServiceAnnotation && extensionPoint?.effectiveQualifiedName == "com.intellij.semContributor") {
    // disallow any parameters
    return false
  }

  for (parameter in list.parameters) {
    if (parameter.isVarArgs) {
      return false
    }

    val type = parameter.type as? PsiClassType ?: return false
    // before resolve, check unqualified name
    val name = type.className
    if (!allowedServiceNames.contains(name)) {
      return false
    }

    val qualifiedName = (type.resolve() ?: return false).qualifiedName
    if (!allowedServiceQualifiedNames.contains(qualifiedName)) {
      return false
    }

    if (isAppLevelExtensionPoint && !isServiceAnnotation && name == "Project") {
      return false
    }
  }

  return true
}

private val interfacesToCheck = THashSet(listOf(
  "com.intellij.codeInsight.daemon.LineMarkerProvider",
  "com.intellij.openapi.fileTypes.SyntaxHighlighterFactory"
))

private val classesToCheck = THashSet(listOf(
  "com.intellij.codeInsight.completion.CompletionContributor",
  "com.intellij.codeInsight.completion.CompletionConfidence",
  "com.intellij.psi.PsiReferenceContributor"
))

private fun isExtensionBean(aClass: UClass): Boolean {
  var found = false
  InheritanceUtil.processSupers(aClass.javaPsi, true) {
    val qualifiedName = it.qualifiedName
    found = (if (it.isInterface) interfacesToCheck else classesToCheck).contains(qualifiedName)
    !found
  }
  return found
}