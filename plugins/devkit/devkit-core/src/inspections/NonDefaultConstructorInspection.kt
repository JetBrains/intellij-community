// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.jvm.JvmClassKind
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameterList
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.util.Processor
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

class NonDefaultConstructorInspection : DevKitUastInspectionBase() {
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
    var isServiceAnnotation = false // hack, allow Project-level @Service
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
      val parameters = method.parameterList
      if (isAllowedParameters(parameters, extensionPoint, isAppLevelExtensionPoint, isServiceAnnotation)) {
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
                                                 "$kind should not have constructor with parameters${suffix}. To not instantiate services in constructor.", true,
                                                 ProblemHighlightType.ERROR, isOnTheFly))
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
    val pointBeanClass = point.beanClass.stringValue
    when (pointBeanClass) {
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
    it.name.startsWith(Extension.IMPLEMENTATION_ATTRIBUTE) && it.value == qualifiedName
  }
}

private fun isAllowedParameters(list: PsiParameterList,
                                extensionPoint: ExtensionPoint?,
                                isAppLevelExtensionPoint: Boolean,
                                isServiceAnnotation: Boolean): Boolean {
  if (list.isEmpty) {
    return true
  }

  // hardcoded for now, later will be generalized
  if (!isServiceAnnotation) {
    if (isAppLevelExtensionPoint || extensionPoint?.effectiveQualifiedName == "com.intellij.semContributor") {
      // disallow any parameters
      return false
    }
  }

  if (list.parametersCount != 1) {
    return false
  }

  val first = list.parameters.first()
  if (first.isVarArgs) {
    return false
  }

  val type = first.type as? PsiClassType ?: return false
  // before resolve, check unqualified name
  if (!(type.className == "Project" || type.className == "Module")) {
    return false
  }

  val qualifiedName = (type.resolve() ?: return false).qualifiedName
  return qualifiedName == "com.intellij.openapi.project.Project" || qualifiedName == "com.intellij.openapi.module.Module"
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
  InheritanceUtil.processSupers(aClass.javaPsi, true, Processor {
    val qualifiedName = it.qualifiedName
    found = (if (it.isInterface) interfacesToCheck else classesToCheck).contains(qualifiedName)
    !found
  })
  return found
}