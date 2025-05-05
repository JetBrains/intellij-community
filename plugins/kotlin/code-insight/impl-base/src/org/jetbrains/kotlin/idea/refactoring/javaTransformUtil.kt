// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring

import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.Factory
import com.intellij.psi.impl.source.tree.SharedImplUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.IncorrectOperationException
import com.intellij.util.VisibilityUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.asJava.*
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinValVar
import org.jetbrains.kotlin.idea.refactoring.changeSignature.toValVar
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtValVarKeywordOwner
import org.jetbrains.kotlin.psi.psiUtil.getElementTextWithContext
import java.lang.annotation.Retention


fun PsiElement.isTrueJavaMethod(): Boolean = this is PsiMethod && this !is KtLightMethod

private fun copyModifierListItems(from: PsiModifierList, to: PsiModifierList, withPsiModifiers: Boolean = true) {
    if (withPsiModifiers) {
        for (modifier in PsiModifier.MODIFIERS) {
            if (from.hasExplicitModifier(modifier)) {
                to.setModifierProperty(modifier, true)
            }
        }
    }
    for (annotation in from.annotations) {
        val annotationName = annotation.qualifiedName ?: continue

        if (Retention::class.java.name != annotationName) {
            to.addAnnotation(annotationName)
        }
    }
}

private fun <T> copyTypeParameters(
    from: T,
    to: T,
    inserter: (T, PsiTypeParameterList) -> Unit
) where T : PsiTypeParameterListOwner, T : PsiNameIdentifierOwner {
    val factory = PsiElementFactory.getInstance((from as PsiElement).project)
    val templateTypeParams = from.typeParameterList?.typeParameters ?: PsiTypeParameter.EMPTY_ARRAY
    if (templateTypeParams.isNotEmpty()) {
        inserter(to, factory.createTypeParameterList())
        val targetTypeParamList = to.typeParameterList
        val newTypeParams = templateTypeParams.map {
            factory.createTypeParameter(it.name!!, it.extendsList.referencedTypes)
        }

        synchronizeList(
            targetTypeParamList,
            newTypeParams,
            { it!!.typeParameters.toList() },
            BooleanArray(newTypeParams.size)
        )
    }
}

fun createJavaMethod(function: KtFunction, targetClass: PsiClass): PsiMethod {
    val template = LightClassUtil.getLightClassMethod(function)
        ?: throw AssertionError("Can't generate light method: ${function.getElementTextWithContext()}")
    return createJavaMethod(template, targetClass)
}

fun createJavaMethod(template: PsiMethod, targetClass: PsiClass): PsiMethod {
    val factory = PsiElementFactory.getInstance(template.project)
    val methodToAdd = if (template.isConstructor) {
        factory.createConstructor(template.name)
    } else {
        factory.createMethod(template.name, template.returnType)
    }
    val method = targetClass.add(methodToAdd) as PsiMethod

    copyModifierListItems(template.modifierList, method.modifierList)
    if (targetClass.isInterface) {
        method.modifierList.setModifierProperty(PsiModifier.FINAL, false)
    }

    copyTypeParameters(template, method) { psiMethod, typeParameterList ->
        psiMethod.addAfter(typeParameterList, psiMethod.modifierList)
    }

    val targetParamList = method.parameterList
    val newParams = template.parameterList.parameters.map {
        val param = factory.createParameter(it.name, it.type)
        copyModifierListItems(it.modifierList!!, param.modifierList!!)
        param
    }

    synchronizeList(
        targetParamList,
        newParams,
        { it.parameters.toList() },
        BooleanArray(newParams.size)
    )

    if (template.modifierList.hasModifierProperty(PsiModifier.ABSTRACT) || targetClass.isInterface) {
        method.body!!.delete()
    } else if (!template.isConstructor) {
        CreateFromUsageUtils.setupMethodBody(method)
    }

    return method
}

fun createJavaField(property: KtNamedDeclaration, targetClass: PsiClass): PsiField {
    val accessorLightMethods = property.getAccessorLightMethods()
    val template = accessorLightMethods.getter
        ?: throw AssertionError("Can't generate light method: ${property.getElementTextWithContext()}")

    val factory = PsiElementFactory.getInstance(template.project)
    val field = targetClass.add(factory.createField(property.name!!, template.returnType!!)) as PsiField

    with(field.modifierList!!) {
        val templateModifiers = template.modifierList
        setModifierProperty(VisibilityUtil.getVisibilityModifier(templateModifiers), true)
        if ((property as KtValVarKeywordOwner).valOrVarKeyword.toValVar() != KotlinValVar.Var || targetClass.isInterface) {
            setModifierProperty(PsiModifier.FINAL, true)
        }

        copyModifierListItems(templateModifiers, this, false)
    }

    return field
}

@OptIn(KaExperimentalApi::class, KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
fun createJavaClass(klass: KtClass, targetClass: PsiClass?, classKind: ClassKind): PsiClass {
    val factory = PsiElementFactory.getInstance(klass.project)
    val className = klass.name!!
    val javaClassToAdd = when (classKind) {
        ClassKind.CLASS -> factory.createClass(className)
        ClassKind.INTERFACE -> factory.createInterface(className)
        ClassKind.ANNOTATION_CLASS -> factory.createAnnotationType(className)
        ClassKind.ENUM_CLASS -> factory.createEnum(className)
        else -> throw AssertionError("Unexpected class kind: ${klass.getElementTextWithContext()}")
    }
    val javaClass = (targetClass?.add(javaClassToAdd) ?: javaClassToAdd) as PsiClass
    val template = klass.toLightClass() ?: KotlinAsJavaSupport.getInstance(klass.project).getFakeLightClass(klass)

    copyModifierListItems(template.modifierList!!, javaClass.modifierList!!)
    if (targetClass?.parent is PsiFile && classKind == ClassKind.CLASS) {
        javaClass.modifierList!!.setModifierProperty(PsiModifier.STATIC, true)
    }
    if (template.isInterface) {
        javaClass.modifierList!!.setModifierProperty(PsiModifier.ABSTRACT, false)
    }

    copyTypeParameters(template, javaClass) { clazz, typeParameterList ->
        clazz.addAfter(typeParameterList, clazz.nameIdentifier)
    }

    // Turning interface to class
    if (!javaClass.isInterface && template.isInterface) {
        val implementsList = factory.createReferenceListWithRole(
            template.extendsList?.referenceElements ?: PsiJavaCodeReferenceElement.EMPTY_ARRAY,
            PsiReferenceList.Role.IMPLEMENTS_LIST
        )

        implementsList?.let { javaClass.implementsList?.replace(it) }
    } else {
        val extendsList = factory.createReferenceListWithRole(
            template.extendsList?.referenceElements ?: PsiJavaCodeReferenceElement.EMPTY_ARRAY,
            PsiReferenceList.Role.EXTENDS_LIST
        )

        extendsList?.let { javaClass.extendsList?.replace(it) }

        val implementsList = factory.createReferenceListWithRole(
            template.implementsList?.referenceElements ?: PsiJavaCodeReferenceElement.EMPTY_ARRAY,
            PsiReferenceList.Role.IMPLEMENTS_LIST
        )

        implementsList?.let { javaClass.implementsList?.replace(it) }
    }

    for (method in template.methods) {
        if (isSyntheticValuesOrValueOfMethod(method)) continue

        val hasParams = method.parameterList.parametersCount > 0
        val needSuperCall = !template.isEnum &&
                (template.superClass?.constructors ?: PsiMethod.EMPTY_ARRAY).all {
                    it.parameterList.parametersCount > 0
                }

        if (method.isConstructor && !(hasParams || needSuperCall)) continue
        with(createJavaMethod(method, javaClass)) {
            if (isConstructor && needSuperCall) {
                body!!.add(factory.createStatementFromText("super();", this))
            }
        }
    }
    if (classKind == ClassKind.ANNOTATION_CLASS && template.methods.isEmpty()) {
        // convert kotlin annotation class ctr parameters to java getters, if "convert to light class" failed
        val psiElementFactory = PsiElementFactory.getInstance(template.project)
        for (ktParameter in klass.primaryConstructorParameters) {
            val name = ktParameter.name ?: break
            val returnType: PsiType = allowAnalysisOnEdt {
                allowAnalysisFromWriteAction {
                    analyze(klass) {
                        ktParameter.typeReference?.type?.asPsiType(klass, false, isAnnotationMethod = true)
                    }
                }
            } ?: break
            val psiMethod = psiElementFactory.createMethod(name, returnType)
            psiMethod.body?.delete()
            javaClass.add(psiMethod)
        }
    }
    return javaClass
}

@Throws(IncorrectOperationException::class)
fun <Parent : PsiElement?, Child : PsiElement?> synchronizeList(
    list: Parent,
    newElements: List<Child>,
    generator: (Parent) -> List<Child>,
    shouldRemoveChild: BooleanArray
) {
    var elementsToRemove: MutableList<Child>? = null
    var elements: List<Child>

    var index = 0
    while (true) {
        elements = generator.invoke(list)
        if (index == newElements.size) break

        if (elementsToRemove == null) {
            elementsToRemove = ArrayList()
            for (i in shouldRemoveChild.indices) {
                if (shouldRemoveChild[i] && i < elements.size) {
                    elementsToRemove.add(elements[i])
                }
            }
        }

        val oldElement = if (index < elements.size) elements[index] else null
        val newElement: Child? = newElements[index]
        if (newElement != null) {
            if (newElement != oldElement) {
                if (oldElement != null && elementsToRemove.contains(oldElement)) {
                    oldElement.delete()
                    index--
                } else {
                    assert(list!!.isWritable) { PsiUtilCore.getVirtualFile(list)!! }
                    list.addBefore(newElement, oldElement)
                    if (list == newElement.parent) {
                        newElement.delete()
                    }
                }
            }
        } else {
            if (newElements.size > 1 && (elements.isNotEmpty() || index < newElements.size - 1)) {
                val anchor = if (index == 0) {
                    list!!.firstChild
                } else {
                    if (index - 1 < elements.size) elements[index - 1] else null
                }
                val charTable = SharedImplUtil.findCharTableByTree(list!!.node)
                val psi = Factory.createSingleLeafElement(JavaTokenType.COMMA, ",", 0, 1, charTable, list.manager).psi
                if (anchor != null) {
                    list.addAfter(psi, anchor)
                } else {
                    list.add(psi)
                }
            }
        }
        index++
    }

    for (i in newElements.size until elements.size) {
        val element = elements[i]
        element!!.delete()
    }
}
