// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.actions.generate

import com.intellij.ide.util.MemberChooser
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.DescriptorMemberChooserObject
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.core.insertMembersAfterAndReformat
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperClassOrAny
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.isNullable
import org.jetbrains.kotlin.utils.addToStdlib.lastIsInstanceOrNull

private fun ClassDescriptor.findDeclaredToString(checkSupers: Boolean): FunctionDescriptor? {
    return findDeclaredFunction("toString", checkSupers) {
        it.modality != Modality.ABSTRACT && it.valueParameters.isEmpty() && it.typeParameters.isEmpty()
    }
}

class KotlinGenerateToStringAction : KotlinGenerateMemberActionBase<KotlinGenerateToStringAction.Info>() {
    companion object {
        private val LOG = Logger.getInstance(KotlinGenerateToStringAction::class.java)

        var KtClassOrObject.adjuster: ((Info) -> Info)? by UserDataProperty(Key.create("ADJUSTER"))
    }

    data class Info(
        val classDescriptor: ClassDescriptor,
        val variablesToUse: List<VariableDescriptor>,
        val generateSuperCall: Boolean,
        val generator: Generator,
        val project: Project
    )

    enum class Generator(@Nls val text: String) {
        SINGLE_TEMPLATE(KotlinBundle.message("action.generate.tostring.template.single")) {
            override fun generate(info: Info): String {
                val className = info.classDescriptor.name.asString()

                return buildString {
                    append("return \"${className.quoteIfNeeded()}(")
                    info.variablesToUse.joinTo(this) {
                        val ref =
                            (DescriptorToSourceUtilsIde.getAnyDeclaration(info.project, it) as PsiNameIdentifierOwner).nameIdentifier!!.text
                        "$ref=${renderVariableValue(it, ref)}"
                    }
                    append(")")
                    if (info.generateSuperCall) {
                        append(" \${super.toString()}")
                    }
                    append("\"")
                }
            }
        },

        MULTIPLE_TEMPLATES(KotlinBundle.message("action.generate.tostring.template.multiple")) {
            override fun generate(info: Info): String {
                val className = info.classDescriptor.name.asString()

                return buildString {
                    if (info.variablesToUse.isNotEmpty()) {
                        append("return \"${className.quoteIfNeeded()}(\" +\n")
                        val varIterator = info.variablesToUse.iterator()
                        while (varIterator.hasNext()) {
                            val it = varIterator.next()
                            val ref = (DescriptorToSourceUtilsIde.getAnyDeclaration(info.project, it) as PsiNameIdentifierOwner)
                                .nameIdentifier!!.text
                            append("\"$ref=${renderVariableValue(it, ref)}")
                            if (varIterator.hasNext()) {
                                append(", ")
                            }
                            append("\" +\n")
                        }
                        append("\")\"")
                    } else {
                        append("return \"$className()\"")
                    }

                    if (info.generateSuperCall) {
                        append(" +\n \" \${super.toString()}\"")
                    }
                }
            }
        };

        protected fun renderVariableValue(variableDescriptor: VariableDescriptor, ref: String): String {
            val type = variableDescriptor.type
            return when {
                KotlinBuiltIns.isArray(type) || KotlinBuiltIns.isPrimitiveArray(type) -> {
                    val dot = if (type.isNullable()) "?." else "."
                    "\${$ref${dot}contentToString()}"
                }
                KotlinBuiltIns.isString(type) -> "'$$ref'"
                else -> "$$ref"
            }
        }

        abstract fun generate(info: Info): String
    }

    override fun isValidForClass(targetClass: KtClassOrObject): Boolean =
        targetClass is KtClass && !targetClass.isAnnotation() && !targetClass.isInterface() || targetClass is KtObjectDeclaration

    public override fun prepareMembersInfo(klass: KtClassOrObject, project: Project, editor: Editor?): Info? {
        return prepareMembersInfo(klass, project, true)
    }

    fun prepareMembersInfo(klass: KtClassOrObject, project: Project, askDetails: Boolean): Info? {
        val context = klass.analyzeWithContent()
        val classDescriptor = context.get(BindingContext.CLASS, klass) ?: return null

        val existingToString = classDescriptor.findDeclaredToString(false)
        if (existingToString != null && askDetails) {
            if (!confirmMemberRewrite(klass, existingToString)) return null

            runWriteAction {
                try {
                    existingToString.source.getPsi()?.delete()
                } catch (e: IncorrectOperationException) {
                    LOG.error(e)
                }
            }
        }

        val superToString = classDescriptor.getSuperClassOrAny().findDeclaredToString(true)!!
        val allowSuperCall = !superToString.builtIns.isMemberOfAny(superToString)

        val properties = getPropertiesToUseInGeneratedMember(klass)
        if (isUnitTestMode() || !askDetails) {
            val info = Info(
                classDescriptor,
                properties.map { context[BindingContext.DECLARATION_TO_DESCRIPTOR, it] as VariableDescriptor },
                false,
                Generator.SINGLE_TEMPLATE,
                project
            )
            return klass.adjuster?.invoke(info)?.let { it.copy(generateSuperCall = it.generateSuperCall && allowSuperCall) } ?: info
        }

        val memberChooserObjects = properties.map { DescriptorMemberChooserObject(it, it.unsafeResolveToDescriptor()) }.toTypedArray()
        val selectedElements = memberChooserObjects.filter { (it.descriptor as? PropertyDescriptor)?.getter?.isDefault ?: true }.toTypedArray()
        val headerPanel = ToStringMemberChooserHeaderPanel(allowSuperCall)
        val chooser = MemberChooser<DescriptorMemberChooserObject>(memberChooserObjects, true, true, project, false, headerPanel).apply {
            title = KotlinBundle.message("action.generate.tostring.name")
            setCopyJavadocVisible(false)
            selectElements(selectedElements)
        }

        if (!klass.hasExpectModifier()) {
            chooser.show()
            if (chooser.exitCode != DialogWrapper.OK_EXIT_CODE) return null
        }

        return Info(classDescriptor,
                    chooser.selectedElements?.map { it.descriptor as VariableDescriptor } ?: emptyList(),
                    headerPanel.isGenerateSuperCall,
                    headerPanel.selectedGenerator,
                    project)
    }

    fun generateToString(targetClass: KtClassOrObject, info: Info): KtNamedFunction {
        val superToString = info.classDescriptor.getSuperClassOrAny().findDeclaredToString(true)!!
        return generateFunctionSkeleton(superToString, targetClass).apply {
            replaceBody {
                KtPsiFactory(project).createBlock(info.generator.generate(info))
            }
        }
    }

    override fun generateMembers(project: Project, editor: Editor?, info: Info): List<KtDeclaration> {
        val targetClass = info.classDescriptor.source.getPsi() as KtClassOrObject
        val prototype = generateToString(targetClass, info)
        val anchor = with(targetClass.declarations) { lastIsInstanceOrNull<KtNamedFunction>() ?: lastOrNull() }
        return insertMembersAfterAndReformat(editor, targetClass, listOf(prototype), anchor)
    }
}