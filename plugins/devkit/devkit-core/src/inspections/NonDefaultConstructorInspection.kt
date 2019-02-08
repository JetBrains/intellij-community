// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.jvm.JvmClassKind
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiParameterList
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.util.SmartList
import gnu.trove.THashSet
import org.jetbrains.idea.devkit.dom.ExtensionPoint
import org.jetbrains.idea.devkit.util.processExtensionsByClassName
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.convert
import org.jetbrains.uast.getLanguagePlugin

internal class NonDefaultConstructorInspection : DevKitUastInspectionBase() {
  override fun checkClass(aClass: UClass, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    val javaPsi = aClass.javaPsi
    // Groovy from test data - ignore it
    if (javaPsi.language.id == "Groovy" || javaPsi.classKind != JvmClassKind.CLASS ||
        PsiUtil.isInnerClass(javaPsi) || PsiUtil.isLocalOrAnonymousClass(javaPsi) ||
        PsiUtil.isAbstractClass(javaPsi)) {
      return null
    }

    val constructors = javaPsi.constructors
    // very fast path - do nothing if no constructors
    if (constructors.isEmpty()) {
      return null
    }

    var extensionPoint: ExtensionPoint? = null
    // fast path - check by qualified name
    if (!isExtensionBean(aClass)) {
      // slow path - check using index
      extensionPoint = findExtensionPoint(aClass, manager.project) ?: return null
    }
    else if (javaPsi.name == "VcsConfigurableEP") {
      // VcsConfigurableEP extends ConfigurableEP but used directly, for now just ignore it as hardcoded exclusion
      return null
    }

    var errors: MutableList<ProblemDescriptor>? = null
    loop@ for (method in constructors) {
      val parameters = method.parameterList
      if (isAllowedParameters(parameters, extensionPoint)) {
        // allow to have empty constructor and extra (e.g. DartQuickAssistIntention)
        return null
      }

      if (errors == null) {
        errors = SmartList()
      }

      // kotlin is not physical, but here only physical is expected, so, convert to uast element and use sourcePsi
      val anchorElement = when {
        method.isPhysical -> method
        else -> aClass.getLanguagePlugin().convert<UMethod>(method, aClass).sourcePsi ?: continue@loop
      }
      errors.add(manager.createProblemDescriptor(anchorElement,
                                                 "Bean extension class should not have constructor with parameters", true,
                                                 ProblemHighlightType.ERROR, isOnTheFly))
    }
    return errors?.toTypedArray()
  }
}

// cannot check com.intellij.codeInsight.intention.IntentionAction by class qualified name because not all IntentionAction used as IntentionActionBean
private fun findExtensionPoint(clazz: UClass, project: Project): ExtensionPoint? {
  var result: ExtensionPoint? = null
  val qualifiedNamed = clazz.qualifiedName ?: return null
  processExtensionsByClassName(project, qualifiedNamed) { tag, point ->
    // check only bean extensions
    if (point.beanClass.value == null) {
      return@processExtensionsByClassName true
    }

    if (tag.name == "className" || tag.subTags.any { it.name == "className" } || checkAttributes(tag, qualifiedNamed)) {
      result = point
      false
    }
    else {
      true
    }
  }
  return result
}

// todo can we use attribute `with` to avoid hardcoding?
private val ignoredTagNames = THashSet(listOf("semContributor", "modelFacade", "scriptGenerator", "editorActionHandler", "editorTypedHandler", "dataImporter", "java.error.fix", "explainPlanProvider"))

// problem - tag
//<lang.elementManipulator forClass="com.intellij.psi.css.impl.CssTokenImpl"
//                         implementationClass="com.intellij.psi.css.impl.CssTokenImpl$Manipulator"/>
// will be found for `com.intellij.psi.css.impl.CssTokenImpl`, but we need to ignore `forClass` and check that we have exact match for implementation attribute
private fun checkAttributes(tag: XmlTag, qualifiedNamed: String): Boolean {
  if (ignoredTagNames.contains(tag.name)) {
    // DbmsExtension passes Dbms instance directly, doesn't need to check
    return false
  }

  return tag.attributes.any {
    it.name.startsWith("implementation") && it.value == qualifiedNamed
  }
}

private fun isAllowedParameters(list: PsiParameterList, extensionPoint: ExtensionPoint?): Boolean {
  if (list.isEmpty) {
    return true
  }

  val area = extensionPoint?.area?.value ?: ExtensionPoint.Area.IDEA_APPLICATION
  val isAppLevelExtensionPoint = area == ExtensionPoint.Area.IDEA_APPLICATION

  // hardcoded for now, later will be generalized
  if (isAppLevelExtensionPoint || extensionPoint?.effectiveQualifiedName == "com.intellij.semContributor") {
    // disallow any parameters
    return false
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

private val interfacesToCheck = THashSet<String>(listOf(
  "com.intellij.codeInsight.daemon.LineMarkerProvider",
  "com.intellij.openapi.fileTypes.SyntaxHighlighterFactory"
))

private val classesToCheck = THashSet<String>(listOf(
  "com.intellij.openapi.extensions.AbstractExtensionPointBean",
  "com.intellij.codeInsight.completion.CompletionContributor",
  "com.intellij.codeInsight.completion.CompletionConfidence",
  "com.intellij.psi.PsiReferenceContributor"
))

private fun isExtensionBean(aClass: UClass): Boolean {
  var p = aClass
  while (true) {
    if (checkInterfaces(p.javaPsi.interfaces)) {
      return true
    }

    p = p.superClass ?: return false
    if (classesToCheck.contains(p.qualifiedName)) {
      return true
    }
  }
}

private fun checkInterfaces(list: Array<PsiClass>): Boolean {
  for (interfaceClass in list) {
    if (interfacesToCheck.contains(interfaceClass.qualifiedName) || checkInterfaces(interfaceClass.interfaces)) {
      return true
    }
  }
  return false
}