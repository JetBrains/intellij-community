package org.jetbrains.plugins.javaFX.fxml.refs

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.*
import com.intellij.lang.jvm.types.JvmSubstitutor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiJvmSubstitutor
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.psi.codeStyle.VariableKind
import com.intellij.psi.util.createSmartPointer
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.util.VisibilityUtil
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil

class JavaFxEventHandlerReferenceQuickFixProvider : UnresolvedReferenceQuickFixProvider<JavaFxEventHandlerReference>() {

  override fun getReferenceClass() = JavaFxEventHandlerReference::class.java

  override fun registerFixes(ref: JavaFxEventHandlerReference, registrar: QuickFixActionRegistrar) {
    val controller = ref.myController ?: return
    if (ref.myEventHandler != null) return
    val element = ref.element ?: return
    val request = CreateEventHandlerRequest(element)
    createMethodActions(controller, request).forEach(registrar::register)
  }
}

class CreateEventHandlerRequest(element: XmlAttributeValue) : CreateMethodRequest {

  private val myProject = element.project
  private val myVisibility = getVisibility(myProject)
  private val myPointer = element.createSmartPointer(myProject)

  override val isValid: Boolean get() {
    val element = myPointer.element
    return element != null && element.value != null
  }

  private val myElement get() = myPointer.element!!

  override val methodName: String get() = myElement.value!!.substring(1)

  override val returnType: ExpectedTypes get() = listOf(expectedType(PsiType.VOID, ExpectedType.Kind.EXACT))

  override val parameters: List<ExpectedParameter> get() {
    val eventType = getEventType(myElement)
    val expectedType = expectedType(eventType, ExpectedType.Kind.EXACT)
    val nameInfo = suggestParamName(myProject, eventType)
    val parameter = ExpectedParameter(nameInfo, listOf(expectedType))
    return listOf(parameter)
  }

  override val modifiers: Collection<JvmModifier> get() = setOf(myVisibility)

  override val annotations: Collection<AnnotationRequest> get() {
    return if (myVisibility != JvmModifier.PUBLIC) {
      listOf(annotationRequest(JavaFxCommonNames.JAVAFX_FXML_ANNOTATION))
    }
    else {
      emptyList()
    }
  }

  override val targetSubstitutor: JvmSubstitutor get() = PsiJvmSubstitutor(myProject, PsiSubstitutor.EMPTY)
}

private fun getVisibility(project: Project): JvmModifier {
  val visibility = JavaCodeStyleSettings.getInstance(project).VISIBILITY
  if (VisibilityUtil.ESCALATE_VISIBILITY == visibility) return JvmModifier.PRIVATE
  if (visibility == PsiModifier.PACKAGE_LOCAL) return JvmModifier.PACKAGE_LOCAL
  return JvmModifier.valueOf(visibility.toUpperCase())
}

private fun suggestParamName(project: Project, eventType: PsiType): SuggestedNameInfo {
  val codeStyleManager = JavaCodeStyleManager.getInstance(project)!!
  val suggestedNameInfo = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, eventType)
  return if (suggestedNameInfo.names.isEmpty()) {
    object : SuggestedNameInfo(arrayOf("e")) {}
  }
  else {
    suggestedNameInfo
  }
}

private fun getEventType(element: XmlAttributeValue): PsiType {
  val parent = element.parent
  if (parent is XmlAttribute) {
    val eventType = JavaFxPsiUtil.getDeclaredEventType(parent)
    if (eventType != null) {
      return eventType
    }
  }
  return PsiType.getTypeByName(JavaFxCommonNames.JAVAFX_EVENT, element.project, element.resolveScope)
}
