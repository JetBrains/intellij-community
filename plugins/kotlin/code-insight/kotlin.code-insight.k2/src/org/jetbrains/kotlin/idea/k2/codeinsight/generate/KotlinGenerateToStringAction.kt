package org.jetbrains.kotlin.idea.k2.codeinsight.generate

import com.intellij.ide.util.MemberChooser
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinPsiElementMemberChooserObject
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinPsiElementMemberChooserObject.Companion.getKotlinMemberChooserObject
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.insertMembersAfterAndReformat
import org.jetbrains.kotlin.idea.k2.codeinsight.generate.GenerateEqualsAndHashCodeUtils.confirmMemberRewrite
import org.jetbrains.kotlin.idea.k2.codeinsight.generate.GenerateEqualsAndHashCodeUtils.findToStringMethodForClass
import org.jetbrains.kotlin.idea.k2.codeinsight.generate.GenerateEqualsAndHashCodeUtils.generateToString
import org.jetbrains.kotlin.idea.k2.codeinsight.generate.GenerateEqualsAndHashCodeUtils.getPropertiesToUseInGeneratedMember
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.UserDataProperty
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier
import org.jetbrains.kotlin.utils.addToStdlib.lastIsInstanceOrNull

class KotlinGenerateToStringAction : KotlinGenerateMemberActionBase<KotlinGenerateToStringAction.Info>() {
    companion object {
        var KtClassOrObject.adjuster: ((Info) -> Info)? by UserDataProperty(Key.create("ADJUSTER"))
    }

    data class Info(
        val klass: KtClassOrObject,
        val variablesToUse: List<KotlinPsiElementMemberChooserObject>,
        val templateName: String = KotlinToStringTemplatesManager.getInstance().defaultTemplate.name
    )

    override fun isValidForClass(targetClass: KtClassOrObject): Boolean =
        targetClass is KtClass && !targetClass.isAnnotation() && !targetClass.isInterface() || targetClass is KtObjectDeclaration

    public override fun prepareMembersInfo(klass: KtClassOrObject, project: Project, editor: Editor): Info? {
        val (preInfo, existsToString) = analyzeInModalWindow(klass, KotlinBundle.message("fix.change.signature.prepare")) {
            val classSymbol = klass.symbol as? KaClassSymbol ?: return@analyzeInModalWindow null
            val toStringMethod = findToStringMethodForClass(classSymbol)
            val existsToString = toStringMethod?.containingSymbol == classSymbol && toStringMethod.origin == KaSymbolOrigin.SOURCE

            val properties = getPropertiesToUseInGeneratedMember(klass)
            val info = Info(klass, properties.map { getKotlinMemberChooserObject(it) })

            (if (isUnitTestMode()) klass.adjuster?.invoke(info)
                ?: info else info) to (toStringMethod?.psi as? KtNamedDeclaration)?.takeIf { existsToString }
        } ?: return null


        if (existsToString != null) {
            if (!confirmMemberRewrite(klass, KotlinBundle.message("action.generate.tostring.name"), existsToString)) return null
            runWriteAction { existsToString.delete() }
        }

        if (isUnitTestMode()) return preInfo

        val memberChooserObjects = preInfo.variablesToUse.toTypedArray()

        val classMembers = memberChooserObjects
        val headerPanel = KotlinToStringTemplateChooserHeaderPanel(project)
        val chooser = MemberChooser(classMembers, true, true, project, false, headerPanel).apply {
                title = KotlinBundle.message("action.generate.tostring.name")
                setCopyJavadocVisible(false)
                selectElements(classMembers)
            }

        if (!klass.hasExpectModifier()) {
            chooser.show()
            if (chooser.exitCode != DialogWrapper.OK_EXIT_CODE) return null
        }

        val selectedTemplate = headerPanel.getSelectedTemplate()
        if (selectedTemplate != null) {
            KotlinToStringTemplatesManager.getInstance().defaultTemplate = selectedTemplate
        }
        return Info(klass, chooser.selectedElements?: emptyList(), selectedTemplate?.name ?: preInfo.templateName)
    }

    override fun generateMembers(project: Project, editor: Editor, info: Info): List<KtDeclaration> {
        val targetClass = info.klass
        val prototype = analyzeInModalWindow(targetClass, KotlinBundle.message("fix.change.signature.prepare")) {
            val templatesManager = KotlinToStringTemplatesManager.getInstance()
            generateToString(
                targetClass,
                             info.variablesToUse.mapNotNull { it.element as? KtNamedDeclaration },
                             (templatesManager.allTemplates.find { it.fileName == info.templateName }
                                 ?: templatesManager.defaultTemplate).template)
        } ?: return emptyList()

        var result: List<KtNamedFunction> = emptyList()
        project.executeWriteCommand(commandName) {
            val anchor = with(targetClass.declarations) { lastIsInstanceOrNull<KtNamedFunction>() ?: lastOrNull() }
            result = insertMembersAfterAndReformat(editor, targetClass, listOf(prototype), anchor)
        }
        return result
    }
}