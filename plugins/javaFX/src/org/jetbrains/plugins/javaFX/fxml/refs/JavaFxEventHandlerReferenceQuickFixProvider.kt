// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.fxml.refs

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiJvmSubstitutor
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.psi.util.createSmartPointer
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.util.VisibilityUtil
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil

class JavaFxEventHandlerReferenceQuickFixProvider : UnresolvedReferenceQuickFixProvider<JavaFxEventHandlerReference>() {

  override fun getReferenceClass(): Class<JavaFxEventHandlerReference> = JavaFxEventHandlerReference::class.java

  override fun registerFixes(ref: JavaFxEventHandlerReference, registrar: QuickFixActionRegistrar) {
    val controller = ref.myController ?: return
    if (ref.myEventHandler != null) return
    val element = ref.element
    val request = CreateEventHandlerRequest(element)
    createMethodActions(controller, request).forEach(registrar::register)
  }
}

class CreateEventHandlerRequest(element: XmlAttributeValue) : CreateMethodRequest {

  private val myProject = element.project
  private val myVisibility = getVisibility(myProject)
  private val myPointer = element.createSmartPointer(myProject)

  override fun isValid(): Boolean = myPointer.element.let {
    it != null && it.value.let { value ->
      value != null && value.length > 2
    }
  }

  private val myElement get() = myPointer.element!!

  override fun getMethodName(): String = myElement.value!!.substring(1)

  override fun getReturnType(): List<ExpectedType> = listOf(expectedType(PsiType.VOID, ExpectedType.Kind.EXACT))

  override fun getExpectedParameters(): List<ExpectedParameter> {
    val eventType = expectedType(getEventType(myElement), ExpectedType.Kind.EXACT)
    val parameter = expectedParameter(eventType)
    return listOf(parameter)
  }

  override fun getParameters(): List<Pair<SuggestedNameInfo, List<ExpectedType>>> = getParameters(expectedParameters, myProject)

  override fun getModifiers(): Set<JvmModifier> = setOf(myVisibility)

  override fun getAnnotations(): List<AnnotationRequest> = if (myVisibility != JvmModifier.PUBLIC) {
    listOf(annotationRequest(JavaFxCommonNames.JAVAFX_FXML_ANNOTATION))
  }
  else {
    emptyList()
  }

  override fun getTargetSubstitutor(): PsiJvmSubstitutor = PsiJvmSubstitutor(myProject, PsiSubstitutor.EMPTY)
}

private fun getVisibility(project: Project): JvmModifier {
  val visibility = JavaCodeStyleSettings.getInstance(project).VISIBILITY
  if (VisibilityUtil.ESCALATE_VISIBILITY == visibility) return JvmModifier.PRIVATE
  if (visibility == PsiModifier.PACKAGE_LOCAL) return JvmModifier.PACKAGE_LOCAL
  return JvmModifier.valueOf(visibility.toUpperCase())
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
