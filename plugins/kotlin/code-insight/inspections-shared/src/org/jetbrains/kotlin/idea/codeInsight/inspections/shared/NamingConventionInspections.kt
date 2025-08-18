// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.*
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.codeInspection.options.OptPane.string
import com.intellij.codeInspection.options.OptionController
import com.intellij.codeInspection.options.RegexValidator
import com.intellij.codeInspection.reference.RefEntity
import com.intellij.codeInspection.reference.RefFile
import com.intellij.codeInspection.reference.RefPackage
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiNameIdentifierOwner
import com.siyeh.ig.BaseGlobalInspection
import org.intellij.lang.annotations.Language
import org.jdom.Element
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.expandedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.projectStructure.isInTestSource
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isExplicitlyIgnoredByName
import org.jetbrains.kotlin.idea.core.packageMatchesDirectoryOrImplicit
import org.jetbrains.kotlin.idea.quickfix.RenameIdentifierFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.jetbrains.kotlin.psi.psiUtil.unwrapNullability
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType
import java.util.regex.PatternSyntaxException

data class NamingRule(val message: String, val matcher: (String) -> Boolean)

private fun findRuleMessage(checkString: String, rules: Array<out NamingRule>): String? {
    for (rule in rules) {
        if (rule.matcher(checkString)) {
            return rule.message
        }
    }

    return null
}

private val START_UPPER = NamingRule(KotlinBundle.message("should.start.with.an.uppercase.letter")) {
    it.getOrNull(0)?.isUpperCase() == false
}

private val START_LOWER = NamingRule(KotlinBundle.message("should.start.with.a.lowercase.letter")) {
    it.getOrNull(0)?.isLowerCase() == false
}

private val NO_UNDERSCORES = NamingRule(KotlinBundle.message("should.not.contain.underscores")) {
    '_' in it
}

private val NO_START_UPPER = NamingRule(KotlinBundle.message("should.not.start.with.an.uppercase.letter")) {
    it.getOrNull(0)?.isUpperCase() == true
}

private val NO_START_UNDERSCORE = NamingRule(KotlinBundle.message("should.not.start.with.an.underscore")) {
    it.startsWith('_')
}

private val NO_MIDDLE_UNDERSCORES = NamingRule(KotlinBundle.message("should.not.contain.underscores.in.the.middle.or.the.end")) {
    '_' in it.substring(1)
}


private val NO_UNDERSCORES_IN_CAMEL_CASE = NamingRule(
    KotlinBundle.message("should.not.contain.underscores.with.camel.case")) {
    it.contains('_') && it.any { it.isUpperCase() } && it.any { it.isLowerCase() }
}

private val NO_BAD_CHARACTERS = NamingRule(KotlinBundle.message("may.contain.only.letters.and.digits")) {
    it.any { c -> c !in 'a'..'z' && c !in 'A'..'Z' && c !in '0'..'9' }
}

private val NO_BAD_CHARACTERS_OR_UNDERSCORE = NamingRule(KotlinBundle.message("may.contain.only.letters.digits.or.underscores")) {
    it.any { c -> c !in 'a'..'z' && c !in 'A'..'Z' && c !in '0'..'9' && c != '_' }
}

private val NO_LOWER = NamingRule(KotlinBundle.message("should.not.contain.lowercase.letter")) {
    it.any { c ->  c.isLowerCase() }
}

class NamingConventionInspectionSettings(
    private val entityName: String,
    @Language("RegExp") val defaultNamePattern: String,
    private val setNamePatternCallback: ((value: String) -> Unit)
) {
    var nameRegex: Regex? = defaultNamePattern.toRegex()

    var namePattern: String = defaultNamePattern
        set(value) {
            field = value
            setNamePatternCallback.invoke(value)
            nameRegex = try {
                value.toRegex()
            } catch (_: PatternSyntaxException) {
                null
            }
        }

    fun verifyName(element: PsiNameIdentifierOwner, holder: ProblemsHolder, additionalCheck: () -> Boolean, rules: Array<NamingRule>) {
        val name = element.name
        val nameIdentifier = element.nameIdentifier
        if (name != null && nameIdentifier != null && nameRegex?.matches(name) == false && additionalCheck()) {
            val message = getNameMismatchMessage(name, rules)
            @NlsSafe
            val descriptionTemplate = "$entityName ${KotlinBundle.message("text.name")} <code>#ref</code> $message #loc"
            holder.registerProblem(
                element.nameIdentifier!!,
                descriptionTemplate,
                RenameIdentifierFix()
            )
        }
    }

    fun getNameMismatchMessage(name: String, rules: Array<NamingRule>): String {
        if (namePattern != defaultNamePattern) {
            return getDefaultErrorMessage()
        }

        return findRuleMessage(name, rules) ?: getDefaultErrorMessage()
    }

    fun getDefaultErrorMessage(): String = KotlinBundle.message("doesn.t.match.regex.0", namePattern)

    fun getOptionsPane(): OptPane = pane(string("namePattern", KotlinBundle.message("text.pattern"), 30, RegexValidator()))
    
    fun getOptionController(): OptionController = OptionController.empty()
        .onValue("namePattern", this::namePattern)
}

sealed class NamingConventionInspection(
    entityName: String,
    @Language("RegExp") defaultNamePattern: String
) : AbstractKotlinInspection() {

    // Serialized inspection state
    @Suppress("MemberVisibilityCanBePrivate")
    var namePattern: String = defaultNamePattern

    private val rules: Array<NamingRule> by lazy(::getNamingRules)

    protected abstract fun getNamingRules(): Array<NamingRule>

    private val namingSettings = NamingConventionInspectionSettings(
        entityName, defaultNamePattern,
        setNamePatternCallback = { value ->
            namePattern = value
        }
    )

    protected fun verifyName(element: PsiNameIdentifierOwner, holder: ProblemsHolder, additionalCheck: () -> Boolean = { true }) {
        namingSettings.verifyName(element, holder, additionalCheck, rules)
    }

    override fun getOptionsPane(): OptPane = namingSettings.getOptionsPane()

    override fun getOptionController(): OptionController = namingSettings.getOptionController()
    
    override fun readSettings(node: Element) {
        super.readSettings(node)
        namingSettings.namePattern = namePattern
    }
}

class ClassNameInspection : NamingConventionInspection(
    KotlinBundle.message("class"),
    "[A-Z][A-Za-z\\d]*"
) {
    override fun getNamingRules(): Array<NamingRule> = arrayOf(START_UPPER, NO_UNDERSCORES, NO_BAD_CHARACTERS)

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitClassOrObject(classOrObject: KtClassOrObject) {
                verifyName(classOrObject, holder)
            }

            override fun visitEnumEntry(enumEntry: KtEnumEntry) {
                // do nothing
            }
        }
    }
}

class EnumEntryNameInspection : NamingConventionInspection(
    KotlinBundle.message("enum.entry"),
    "[A-Z]([A-Za-z\\d]*|[A-Z_\\d]*)"
) {
  override fun getNamingRules(): Array<NamingRule> = arrayOf(
      START_UPPER, NO_START_UNDERSCORE, NO_BAD_CHARACTERS_OR_UNDERSCORE, NO_UNDERSCORES_IN_CAMEL_CASE)

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return enumEntryVisitor { enumEntry -> verifyName(enumEntry, holder) }
    }
}

class FunctionNameInspection : NamingConventionInspection(
    KotlinBundle.message("function"),
    "[a-z][A-Za-z\\d]*"
) {
    override fun getNamingRules(): Array<NamingRule> = arrayOf(START_LOWER, NO_UNDERSCORES, NO_BAD_CHARACTERS)

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return namedFunctionVisitor { function ->
            if (function.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
                return@namedFunctionVisitor
            }
            val virtualFile = function.containingFile.virtualFile
            if (!ProjectFileIndex.getInstance(function.project).isInTestSource(virtualFile)) {
                verifyName(function, holder) { !function.isFactoryFunction() }
            }
        }
    }
}

private fun KtNamedFunction.isFactoryFunction(): Boolean {
    val functionName = this.name ?: return false
    val typeElement = typeReference?.typeElement
    return if (typeElement != null) {
        (typeElement.unwrapNullability() as? KtUserType)?.referencedName == functionName
    } else {
        isFactoryFunctionByAnalyze()
    }
}

private fun KtNamedFunction.isFactoryFunctionByAnalyze(): Boolean = analyze(this) {
    val functionName = name ?: return false
    val returnType = returnType

    return returnType.hasShortName(functionName)
            || returnType.allSupertypes.any { it.hasShortName(functionName) }
}

context(_: KaSession)
private fun KaType.hasShortName(shortName: String): Boolean {
    val typeShortName =
        expandedSymbol
            ?.classId
            ?.relativeClassName
            ?.takeUnless(FqName::isRoot)
            ?.shortName()
            ?.identifierOrNullIfSpecial
            ?: return false

    return shortName == typeShortName
}

class TestFunctionNameInspection : NamingConventionInspection(
    KotlinBundle.message("test.function"),
    "[a-z][A-Za-z_\\d]*"
) {
    override fun getNamingRules(): Array<NamingRule> = arrayOf(START_LOWER, NO_BAD_CHARACTERS)

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return namedFunctionVisitor { function ->
            val virtualFile = function.containingFile.virtualFile
            if (!ProjectFileIndex.getInstance(function.project).isInTestSource(virtualFile)) {
                return@namedFunctionVisitor
            }
            if (function.nameIdentifier?.text?.startsWith("`") == true) {
                return@namedFunctionVisitor
            }
            verifyName(function, holder) { !function.isFactoryFunction() }
        }
    }
}

abstract class PropertyNameInspectionBase protected constructor(
    private val kind: PropertyKind,
    entityName: String,
    defaultNamePattern: String
) : NamingConventionInspection(entityName, defaultNamePattern) {

    protected enum class PropertyKind { NORMAL, OBJECT_PRIVATE, PRIVATE, OBJECT_OR_TOP_LEVEL, CONST, LOCAL }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid = object : KtVisitorVoid() {
        override fun visitProperty(property: KtProperty) {
            if (property.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return
            if (property.isExplicitlyIgnoredByName()) return
            if (property.getKind() == kind) {
                verifyName(property, holder, additionalCheck = { additionalPropertyCheck(property) })
            }
        }

        override fun visitParameter(parameter: KtParameter) {
            if (parameter.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return
            if (parameter.isExplicitlyIgnoredByName()) return
            if (parameter.getKind() == kind) {
                verifyName(parameter, holder)
            }
        }

        override fun visitDestructuringDeclarationEntry(multiDeclarationEntry: KtDestructuringDeclarationEntry) {
            if (multiDeclarationEntry.isExplicitlyIgnoredByName()) return
            if (kind == PropertyKind.LOCAL) {
                verifyName(multiDeclarationEntry, holder)
            }
        }
    }

    protected open fun additionalPropertyCheck(property: KtNamedDeclaration): Boolean = true

    private fun KtProperty.getKind(): PropertyKind {
        val private = visibilityModifierType() == KtTokens.PRIVATE_KEYWORD
        return when {
            isLocal -> PropertyKind.LOCAL

            hasModifier(KtTokens.CONST_KEYWORD) && !isTopLevel -> PropertyKind.CONST

            private && containingClassOrObject is KtObjectDeclaration -> PropertyKind.OBJECT_PRIVATE

            !private && (containingClassOrObject is KtObjectDeclaration) || isTopLevel -> PropertyKind.OBJECT_OR_TOP_LEVEL

            private -> PropertyKind.PRIVATE

            else -> PropertyKind.NORMAL
        }
    }

    private fun KtParameter.getKind(): PropertyKind = when {
        isPrivate() -> PropertyKind.PRIVATE

        hasValOrVar() -> PropertyKind.NORMAL

        else -> PropertyKind.LOCAL
    }
}

class PropertyNameInspection : PropertyNameInspectionBase(
    PropertyKind.NORMAL,
    KotlinBundle.message("property"),
    "[a-z][A-Za-z\\d]*"
) {
    override fun getNamingRules(): Array<NamingRule> = arrayOf(START_LOWER, NO_UNDERSCORES, NO_BAD_CHARACTERS)
}

class ObjectPropertyNameInspection : PropertyNameInspectionBase(
    PropertyKind.OBJECT_OR_TOP_LEVEL,
    KotlinBundle.message("object.or.top.level.property"),
    "[A-Za-z][_A-Za-z\\d]*",
) {
    override fun getNamingRules(): Array<NamingRule> = arrayOf(NO_START_UNDERSCORE, NO_BAD_CHARACTERS_OR_UNDERSCORE, NO_BAD_CHARACTERS)
}

class ObjectPrivatePropertyNameInspection : PropertyNameInspectionBase(
    PropertyKind.OBJECT_PRIVATE,
    KotlinBundle.message("object.private.property"),
    "_?[A-Za-z][_A-Za-z\\d]*",
) {
    override fun getNamingRules(): Array<NamingRule> = arrayOf(NO_BAD_CHARACTERS_OR_UNDERSCORE)

}

class PrivatePropertyNameInspection : PropertyNameInspectionBase(
    PropertyKind.PRIVATE,
    KotlinBundle.message("private.property"),
    "_?[a-z][A-Za-z\\d]*"
) {
    override fun getNamingRules(): Array<NamingRule> = arrayOf(NO_MIDDLE_UNDERSCORES, NO_START_UPPER, NO_BAD_CHARACTERS_OR_UNDERSCORE)
}

class ConstPropertyNameInspection : PropertyNameInspectionBase(
    PropertyKind.CONST,
    KotlinBundle.message("const.property"),
    "[A-Z][_A-Z\\d]*"
) {
    override fun getNamingRules(): Array<NamingRule> = arrayOf(NO_LOWER, NO_BAD_CHARACTERS)

    override fun additionalPropertyCheck(property: KtNamedDeclaration): Boolean =
        property.name != "serialVersionUID"
}

class LocalVariableNameInspection : PropertyNameInspectionBase(
    PropertyKind.LOCAL,
    KotlinBundle.message("local.variable"),
    "[a-z][A-Za-z\\d]*"
) {
    override fun getNamingRules(): Array<NamingRule> = arrayOf(START_LOWER, NO_UNDERSCORES, NO_BAD_CHARACTERS)
}

@Suppress("InspectionDescriptionNotFoundInspection")
private class PackageNameInspectionLocal(
    val parentInspection: InspectionProfileEntry,
    val namingSettings: NamingConventionInspectionSettings
) : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return packageDirectiveVisitor { directive ->
            val packageNameExpression = directive.packageNameExpression ?: return@packageDirectiveVisitor

            val checkResult = checkPackageDirective(directive, namingSettings) ?: return@packageDirectiveVisitor

            val descriptionTemplate = checkResult.toProblemTemplateString()

            holder.registerProblem(
                packageNameExpression,
                descriptionTemplate,
                RenamePackageFix()
            )
        }
    }

    private class RenamePackageFix : RenameIdentifierFix() {
        override fun getElementToRename(element: PsiElement): PsiElement? {
            val packageDirective = element as? KtPackageDirective ?: return null
            return JavaPsiFacade.getInstance(element.project).findPackage(packageDirective.qualifiedName)
        }
    }

    override fun getShortName(): String = parentInspection.shortName
    override fun getDisplayName(): String = parentInspection.displayName
}

private fun checkPackageDirective(directive: KtPackageDirective, namingSettings: NamingConventionInspectionSettings): CheckResult? {
    return checkQualifiedName(directive.qualifiedName, namingSettings)
}

private val PART_RULES: Array<NamingRule> = arrayOf(NO_BAD_CHARACTERS_OR_UNDERSCORE, NO_START_UPPER)

private fun checkQualifiedName(qualifiedName: String, namingSettings: NamingConventionInspectionSettings): CheckResult? {
    if (qualifiedName.isEmpty() || namingSettings.nameRegex?.matches(qualifiedName) != false) {
        return null
    }

    val partErrorMessage = if (namingSettings.namePattern == namingSettings.defaultNamePattern) {
        qualifiedName.split('.').asSequence()
            .mapNotNull { part -> findRuleMessage(part, PART_RULES) }
            .firstOrNull()
    } else {
        null
    }

    return if (partErrorMessage != null) {
        CheckResult(partErrorMessage, true)
    } else {
        CheckResult(namingSettings.getDefaultErrorMessage(), false)
    }
}

private data class CheckResult(val errorMessage: String, val isForPart: Boolean) {
    @NlsSafe
    fun toErrorMessage(qualifiedName: String): String {
        return KotlinBundle.message("package.name") + if (isForPart) {
            " <code>$qualifiedName</code> ${KotlinBundle.message("text.part")} $errorMessage"
        } else {
            " <code>$qualifiedName</code> $errorMessage"
        }
    }

    @NlsSafe
    fun toProblemTemplateString(): String {
        return KotlinBundle.message("package.name") + if (isForPart) {
            " <code>#ref</code> ${KotlinBundle.message("text.part")} $errorMessage #loc"
        } else {
            " <code>#ref</code> $errorMessage #loc"
        }
    }
}

class PackageNameInspection : BaseGlobalInspection() {
    private val DEFAULT_PACKAGE_NAME_PATTERN = "[a-z_][a-zA-Z\\d_]*(\\.[a-z_][a-zA-Z\\d_]*)*"

    // Serialized setting
    @Suppress("MemberVisibilityCanBePrivate")
    var namePattern: String = DEFAULT_PACKAGE_NAME_PATTERN

    private val namingSettings = NamingConventionInspectionSettings(
        KotlinBundle.message("text.Package"),
        DEFAULT_PACKAGE_NAME_PATTERN,
        setNamePatternCallback = { value ->
            namePattern = value
        }
    )

    override fun checkElement(
        refEntity: RefEntity,
        analysisScope: AnalysisScope,
        inspectionManager: InspectionManager,
        globalInspectionContext: GlobalInspectionContext
    ): Array<CommonProblemDescriptor>? {
        when (refEntity) {
            is RefFile -> {
                val psiFile = refEntity.psiElement
                if (psiFile is KtFile
                    && !InjectedLanguageManager.getInstance(psiFile.project).isInjectedFragment(psiFile)
                    && !psiFile.packageMatchesDirectoryOrImplicit()) {
                    val packageDirective = psiFile.packageDirective
                    if (packageDirective != null) {
                        val qualifiedName = packageDirective.qualifiedName
                        val checkResult = checkPackageDirective(packageDirective, namingSettings)
                        if (checkResult != null) {
                            return arrayOf(inspectionManager.createProblemDescriptor(checkResult.toErrorMessage(qualifiedName)))
                        }
                    }
                }
            }

            is RefPackage -> {
                @NonNls val name = StringUtil.getShortName(refEntity.getQualifiedName())
                if (name.isEmpty() || InspectionsBundle.message("inspection.reference.default.package") == name) {
                    return null
                }

                val checkResult = checkQualifiedName(name, namingSettings)
                if (checkResult != null) {
                    return arrayOf(inspectionManager.createProblemDescriptor(checkResult.toErrorMessage(name)))
                }
            }

            else -> {
                return null
            }
        }

        return null
    }

    override fun readSettings(element: Element) {
        super.readSettings(element)
        namingSettings.namePattern = namePattern
    }

    override fun getOptionsPane(): OptPane = namingSettings.getOptionsPane()

    override fun getOptionController(): OptionController = namingSettings.getOptionController()

    override fun getSharedLocalInspectionTool(): LocalInspectionTool {
        return PackageNameInspectionLocal(this, namingSettings)
    }
}
