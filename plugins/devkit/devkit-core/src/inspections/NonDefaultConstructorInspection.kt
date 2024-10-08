// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.client.ClientKind
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameterList
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.util.SmartList
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.dom.ExtensionPoint
import org.jetbrains.idea.devkit.dom.ExtensionPoint.Area
import org.jetbrains.idea.devkit.util.locateExtensionsByPsiClass
import org.jetbrains.idea.devkit.util.processExtensionDeclarations
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.convertOpt
import java.util.*

private const val serviceBeanFqn = "com.intellij.openapi.components.ServiceDescriptor"

internal class NonDefaultConstructorInspection : DevKitUastInspectionBase(UClass::class.java) {
  override fun checkClass(aClass: UClass, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    val javaPsi = aClass.javaPsi
    // Groovy from test data - ignore it
    if (javaPsi.language.id == "Groovy" || !ExtensionUtil.isExtensionPointImplementationCandidate(javaPsi) ||
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
    var serviceClientKind: ClientKind? = null
    // hack, allow Project-level @Service
    var isServiceAnnotation = false
    var extensionPoint: ExtensionPoint? = null
    if (isLightService(javaPsi)) {
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
      if (isService) {
        for (candidate in locateExtensionsByPsiClass(javaPsi)) {
          val extensionTag = candidate.pointer.element ?: continue
          val clientName = extensionTag.getAttribute("client")?.value ?: continue

          val kind = when (clientName.lowercase(Locale.US)) {
            "local" -> ClientKind.LOCAL
            "controller" -> ClientKind.CONTROLLER
            "guest" -> ClientKind.GUEST
            "owner" -> ClientKind.OWNER
            "remote" -> ClientKind.REMOTE
            "frontend" -> ClientKind.FRONTEND
            "all" -> ClientKind.ALL
            else -> null
          }
          if (serviceClientKind == null) {
            serviceClientKind = kind
          }
          else if (serviceClientKind != kind) {
            serviceClientKind = ClientKind.ALL
          }
        }
      }
    }

    val isAppLevelExtensionPoint = area == null || area == Area.IDEA_APPLICATION

    var errors: MutableList<ProblemDescriptor>? = null
    loop@ for (method in constructors) {
      if (isAllowedParameters(method.parameterList, extensionPoint, isAppLevelExtensionPoint, serviceClientKind, isServiceAnnotation)) {
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


      @NlsSafe val kind = if (isService) DevKitBundle.message("inspections.non.default.warning.type.service") else DevKitBundle.message("inspections.non.default.warning.type.extension")
      @Nls val suffix =
        if (area == null) DevKitBundle.message("inspections.non.default.warning.suffix.project.or.module")
        else {
          when {
            isAppLevelExtensionPoint -> ""
            area == Area.IDEA_PROJECT -> DevKitBundle.message("inspections.non.default.warning.suffix.project")
            else -> DevKitBundle.message("inspections.non.default.warning.suffix.module")
          }
        }
      errors.add(manager.createProblemDescriptor(anchorElement,
                                                 DevKitBundle.message("inspections.non.default.warning.and.suffix.message", kind, suffix),
                                                 true,
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
    if (point.name.value == "psi.symbolReferenceProvider") {
      return@processExtensionDeclarations true
    }

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
        if (tag.name == "className" || tag.subTags.any {
            it.name == "className" && (strictMatch || it.textMatches(qualifiedName))
          } || checkAttributes(tag, qualifiedName)) {
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
@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
@NonNls
private val ignoredTagNames = java.util.Set.of("semContributor", "modelFacade", "scriptGenerator",
                                               "editorActionHandler", "editorTypedHandler",
                                               "dataImporter", "java.error.fix", "explainPlanProvider", "typeIcon")

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

@NonNls
private val allowedClientSessionsQualifiedNames = setOf(
  "com.intellij.openapi.client.ClientSession",
)

@NonNls
private val allowedClientAppSessionsQualifiedNames = setOf(
  "com.intellij.openapi.client.ClientAppSession",
  "com.jetbrains.rdserver.core.GuestAppSession",
  "com.jetbrains.rdserver.core.RemoteAppSession",
) + allowedClientSessionsQualifiedNames

@NonNls
private val allowedClientProjectSessionsQualifiedNames = setOf(
  "com.intellij.openapi.client.ClientProjectSession",
  "com.jetbrains.rdserver.core.GuestProjectSession",
  "com.jetbrains.rdserver.core.RemoteProjectSession",
) + allowedClientSessionsQualifiedNames

@NonNls
private val allowedServiceQualifiedNames = setOf(
  "com.intellij.openapi.project.Project",
  "com.intellij.openapi.module.Module",
  "com.intellij.util.messages.MessageBus",
  "com.intellij.openapi.options.SchemeManagerFactory",
  "com.intellij.openapi.editor.actionSystem.TypedActionHandler",
  "com.intellij.database.Dbms",
  "kotlinx.coroutines.CoroutineScope"
) + allowedClientAppSessionsQualifiedNames + allowedClientProjectSessionsQualifiedNames

private val allowedServiceNames = allowedServiceQualifiedNames.mapTo(HashSet(allowedServiceQualifiedNames.size)) { it.substringAfterLast('.') }

private fun isAllowedParameters(list: PsiParameterList,
                                extensionPoint: ExtensionPoint?,
                                isAppLevelExtensionPoint: Boolean,
                                clientKind: ClientKind?,
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

    if (!checkPerClientServices(clientKind, isAppLevelExtensionPoint, qualifiedName)) {
      return false
    }
  }

  return true
}

private fun checkPerClientServices(kind: ClientKind?, isAppLevel: Boolean, qualifiedName: String?): Boolean {
  val isPerClient = kind != null
  val hasProjectSessionDeps = allowedClientProjectSessionsQualifiedNames.contains(qualifiedName)
  val hasAppSessionDeps = allowedClientAppSessionsQualifiedNames.contains(qualifiedName)

  // non per-client injecting per-client stuff
  if (!isPerClient) {
    return !hasProjectSessionDeps && !hasAppSessionDeps
  }

  // app-level per-client injecting project-level stuff
  if (isAppLevel && hasProjectSessionDeps && !hasAppSessionDeps) {
    return false
  }

  // project-level per-client injecting app-level stuff. Technically okay, but probably not something user wants
  if (!isAppLevel && !hasProjectSessionDeps && hasAppSessionDeps) {
    return false
  }

  // not remote-only per-client injecting remote-only stuff
  if (kind != ClientKind.REMOTE &&
      kind != ClientKind.GUEST &&
      kind != ClientKind.CONTROLLER &&
      qualifiedName?.startsWith("com.jetbrains.rdserver.core") == true) {
    return false
  }

  return true
}

private val interfacesToCheck = HashSet(listOf(
  "com.intellij.codeInsight.daemon.LineMarkerProvider",
  "com.intellij.openapi.fileTypes.SyntaxHighlighterFactory"
))

private val classesToCheck = HashSet(listOf(
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