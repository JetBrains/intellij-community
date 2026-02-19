// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.groovy

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RawText
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.groovy.DependencyNode.Dependency
import org.jetbrains.kotlin.idea.groovy.DependencyParser.parseAsInterpolatedStringDependency
import org.jetbrains.kotlin.idea.groovy.DependencyParser.parseAsNamedArgumentDependency
import org.jetbrains.kotlin.idea.groovy.DependencyParser.parseAsStringLiteralDependency
import org.jetbrains.kotlin.idea.groovy.DependencyParser.parseExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringContent
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrNamedArgumentsOwner
import org.jetbrains.plugins.groovy.lang.psi.impl.booleanValue
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.stringValue

@ApiStatus.Internal
class GroovyLibraryDependenciesToBuildGradleKtsCopyPastePreprocessor : CopyPastePreProcessor {
    override fun preprocessOnCopy(file: PsiFile?, startOffsets: IntArray?, endOffsets: IntArray?, text: String?): String? {
        return null
    }

    override fun preprocessOnPaste(project: Project, file: PsiFile, editor: Editor, text: String, rawText: RawText?): String {
        if (!file.isGradleKtsFile()) return text
        if (!text.looksLikeDependenciesBlock()) return text
        val nodes = DependencyParser.parse(text, project) ?: return text
        return DependencyRenderer.render(nodes)
    }

    private fun PsiFile.isGradleKtsFile(): Boolean {
        return FileUtilRt.extensionEquals(name, "gradle.kts")
    }

    /**
     * Attempts to determine if a given text is a block of Gradle dependency declarations.
     * Due to the possible complexity of such declarations, this method may produce false positives.
     *
     * The method checks if there is at least one line that starts with one of the dependency configurations.
     *
     * Here are some complex examples from the Gradle documentation:
     * https://docs.gradle.org/current/userguide/declaring_dependencies_basics.html
     *
     * ```groovy
     * runtimeOnly 'org.springframework:spring-core:2.5',
     *             'org.springframework:spring-aop:2.5'
     * runtimeOnly(
     *    [group: 'org.springframework', name: 'spring-core', version: '2.5'],
     *    [group: 'org.springframework', name: 'spring-aop', version: '2.5']
     * )
     * ```
     */
    private fun String.looksLikeDependenciesBlock(): Boolean {
        return lineSequence().any { line ->
            val limeTrimmed = line.trim()
            allDependenciesConfigurations.any { limeTrimmed.startsWith(it) }
        }
    }
}

/**
 * Attempts to parse the given text as Gradle Groovy dependency declarations.
 * It tries to cover the most commonly used patterns while being safe to avoid generating incorrect code.
 * Multiple formats of dependencies are supported, and they can be found in tests
 * [org.jetbrains.kotlin.idea.groovy.GroovyLibraryDependenciesToBuildGradleKtsCopyPastePreprocessorTestGenerated].
 *
 * Some supported cases taken from the Gradle doc:
 * https://docs.gradle.org/current/userguide/declaring_dependencies_basics.html
 *
 * ```groovy
 *     runtimeOnly group: 'org.springframework', name: 'spring-core', version: '2.5'
 *     runtimeOnly 'org.springframework:spring-core:2.5',
 *             'org.springframework:spring-aop:2.5'
 *     runtimeOnly(
 *         [group: 'org.springframework', name: 'spring-core', version: '2.5'],
 *         [group: 'org.springframework', name: 'spring-aop', version: '2.5']
 *     )
 *     runtimeOnly('org.hibernate:hibernate:3.0.5') {
 *         transitive = true
 *     }
 *     runtimeOnly group: 'org.hibernate', name: 'hibernate', version: '3.0.5', transitive: true
 *     runtimeOnly(group: 'org.hibernate', name: 'hibernate', version: '3.0.5') {
 *         transitive = true
 *     }
 * ```
 *
 * The [DependencyParser] operates by using Groovy PSI, and it does not perform resolution, only syntax tree analysis.
 * So it's limited in what it can do.
 */
private object DependencyParser {

    /**
     * Interprets [text] as a list of Gradle dependencies and attempts to parse it.
     *
     * If at least one statement fails to be parsed, the function returns `null`.
     *
     * Additionally, tries to preserve comments
     *
     * @return A parsed list of dependencies if successful, or `null` if the text does not appear to represent a list of Groovy dependencies.
     */
    fun parse(text: String, project: Project): List<DependencyNode>? {
        val groovyFile = GroovyPsiElementFactory.getInstance(project).createGroovyFile(
            text,
            /* isPhysical = */ false,
            /* context = */ null
        )

        val result = mutableListOf<DependencyNode>()
        for (statement in groovyFile.children) {
            when {
                statement is GrMethodCall -> {
                    val dependencies = statement.parseAsDependencies() ?: return null
                    result += dependencies
                    result += statement.collectComments()
                }

                statement is PsiComment -> {
                    result += DependencyNode.Comment(statement)
                }

                statement is PsiWhiteSpace -> {
                }

                statement.elementType == GroovyElementTypes.NL /*new line*/ -> {
                }

                else -> return null
            }
        }
        return result
    }

    /**
     * Collects all commits that are inside the dependency to avoid loosing them
     */
    private fun GrMethodCall.collectComments(): List<DependencyNode.Comment> {
        return collectDescendantsOfType<PsiComment>().map { DependencyNode.Comment(it) }
    }

    /**
     * Parses a single method call as a list of dependencies.
     *
     * A single Groovy call can represent multiple declared dependencies, e.g.,
     *
     * ```groovy
     * runtimeOnly 'org.springframework:spring-core:2.5',
     *             'org.springframework:spring-aop:2.5'
     * ```
     *
     * The list of supported dependency configurations is in [allDependenciesConfigurations].
     *
     * @see DependencyParser
     */
    private fun GrMethodCall.parseAsDependencies(): List<Dependency>? {
        if (isProbablyKotlinCode()) return null
        val isTransitiveByClosureBlock = when (closureArguments.size) {
            1 -> {
                closureArguments.single().parseTransitiveArgument() ?: return null
            }

            0 -> {
                null
            }

            else -> {
                return null
            }
        }
        val configuration = callReference?.methodName ?: return null
        if (configuration !in allDependenciesConfigurations) return null
        parseAsExpressionArgumentDependencies(configuration, isTransitiveByClosureBlock)?.let { return it }
        parseAsNamedArgumentDependencies(configuration, isTransitiveByClosureBlock)?.let { return it }
        return null
    }

    /**
     * Parses closures of the kind:
     *
     * ```groovy
     * SOME_CALL {
     *   transitive = true/false
     * }
     * ```
     *
     * For more complex closures, returns `null`.
     *
     * @return The value of the `transitive` property if it is set, or `null` if the closure does not match the pattern or is too complex.
     */
    private fun GrClosableBlock.parseTransitiveArgument(): Boolean? {
        if (parameters.isNotEmpty()) return null
        val assignmentExpression = statements.singleOrNull() as? GrAssignmentExpression ?: return null
        if (assignmentExpression.isOperatorAssignment) return null

        val lValue = assignmentExpression.lValue as? GrReferenceExpression ?: return null
        if (lValue.isQualified || lValue.referenceName != "transitive") return null

        val rValue = assignmentExpression.rValue as? GrLiteral ?: return null
        return rValue.booleanValue()
    }

    /**
     * Parses dependency declarations of the kind:
     *
     * ```groovy
     * runtimeOnly 'org.springframework:spring-core:2.5',
     *             'org.springframework:spring-aop:2.5'
     * runtimeOnly(
     *        [group: 'org.springframework', name: 'spring-core', version: '2.5'],
     *        [group: 'org.springframework', name: 'spring-aop', version: '2.5']
     * )
     * runtimeOnly('org.hibernate:hibernate:3.0.5')
     * ```
     */
    private fun GrMethodCall.parseAsExpressionArgumentDependencies(
        configuration: String,
        isTransitiveByClosureBlock: Boolean?
    ): List<Dependency>? {
        if (namedArguments.isNotEmpty()) return null
        val expressionArguments = expressionArguments.ifEmpty { return null }
        val result = mutableListOf<Dependency>()
        for (expression in expressionArguments) {
            val newDependencies = when {
                expression is GrListOrMap && !expression.isMap -> {
                    // we parse arrays here and not in [parseExpression] to avoid accepting nested arrays
                    expression.parseAsArrayDependencies(configuration, isTransitiveByClosureBlock)
                }

                else -> {
                    expression.parseExpression(configuration, isTransitiveByClosureBlock)?.let { listOf(it) }
                }
            } ?: return null
            result += newDependencies

        }
        return result
    }


    /**
     * Parses dependencies of the kind `runtimeOnly group: 'org.springframework', name: 'spring-core', version: '2.5'`.
     */
    private fun GrMethodCall.parseAsNamedArgumentDependencies(
        configuration: String,
        isTransitiveByClosureBlock: Boolean?
    ): List<Dependency>? {
        if (expressionArguments.isNotEmpty()) return null
        return argumentList.parseAsNamedArgumentDependency(configuration, isTransitiveByClosureBlock)?.let { listOf(it) }
    }

    /**
     * If the code looks like a Kotlin call, i.e., `implementation("a:b:c")`, it's better to be safe and not try converting it to Kotlin.
     */
    private fun GrMethodCall.isProbablyKotlinCode(): Boolean {
        if (closureArguments.isNotEmpty()) return false
        if (namedArguments.isNotEmpty()) return false
        if (argumentList.leftParen == null) {
            // no-parenthesis call, so not a Kotlin call
            return false
        }
        val singleExpressionArgument = expressionArguments.singleOrNull() as? GrLiteral ?: return false
        if (singleExpressionArgument.firstChild.elementType == GroovyElementTypes.STRING_DQ) {
            // if it has a double-quoted string literal, it may be Kotlin, not Groovy
            return true
        }
        return false
    }


    /**
     * Tries to parse some Groovy expression which can be used for Gradle dependency declaration.
     *
     * Supports:
     * - plain strings, see [parseAsStringLiteralDependency]
     * - interpolated strings, see [parseAsInterpolatedStringDependency]
     * - named args, see [parseAsNamedArgumentDependency]
     */
    private fun GrExpression.parseExpression(
        configuration: String,
        isTransitiveByClosureBlock: Boolean?,
    ): Dependency? {
        return when (this) {
            is GrLiteralImpl -> {
                val value = stringValue() ?: return null
                value.parseAsStringLiteralDependency(configuration, isTransitiveByClosureBlock)
            }

            is GrString -> {
                parseAsInterpolatedStringDependency(configuration, isTransitiveByClosureBlock)
            }

            is GrListOrMap -> {
                when {
                    isMap -> parseAsNamedArgumentDependency(configuration, isTransitiveByClosureBlock)
                    else -> null
                }
            }

            else -> null
        }
    }


    /**
     * Parses an array of dependencies supported by [parseExpression].
     */
    private fun GrListOrMap.parseAsArrayDependencies(configuration: String, isTransitiveByClosureBlock: Boolean?): List<Dependency>? {
        val expressions = initializers.ifEmpty { return null }
        val result = mutableListOf<Dependency>()
        for (expression in expressions) {
            val dependency = expression.parseExpression(configuration, isTransitiveByClosureBlock) ?: return null
            result += dependency
        }
        return result
    }

    /**
     * Parses a named argument object of the kind `group: 'org.springframework', name: 'spring-core', version: '2.5'`.
     *
     * It can be either a single dependency declaration like `api group: 'org.springframework', name: 'spring-core', version: '2.5'`
     * or part of an array of dependency declarations like `api [group: 'org.springframework', name: 'spring-core', version: '2.5', ...]`.
     *
     */
    private fun GrNamedArgumentsOwner.parseAsNamedArgumentDependency(
        configuration: String,
        isTransitiveByClosureBlock: Boolean?
    ): Dependency? {
        val transitive: Boolean?
        when (namedArguments.size) {
            3 -> {
                if (!hasOnly("group", "name", "version")) return null
                transitive = isTransitiveByClosureBlock
            }

            4 -> {
                if (isTransitiveByClosureBlock != null) return null
                if (!hasOnly("group", "name", "version", "transitive")) return null
                transitive = findNamedArgument("transitive")?.let { transitiveExpression ->
                    transitiveExpression.expression?.booleanValue() ?: return null
                }
            }

            else -> return null
        }

        return Dependency(
            configuration = configuration,
            group = findNamedArgument("group")?.expression?.asCoordinatePart() ?: return null,
            name = findNamedArgument("name")?.expression?.asCoordinatePart() ?: return null,
            version = findNamedArgument("version")?.expression?.asCoordinatePart() ?: return null,
            transitive = transitive,
            format = Dependency.Format.NamedArguments,
        )
    }

    private fun GrNamedArgumentsOwner.hasOnly(vararg names: String): Boolean {
        return namedArguments.size == names.size && namedArguments.all { it.labelName in names }
    }


    /**
     * Parses the `GrString` as a dependency string in the format "group:name:version" and returns a `Dependency` object.
     *
     * Each of "group", "name", "version", "transitive" can be either string or a simple reference (identifier) to support
     * Dependency declarations like `testCompileOnly "org.hibernate:hibernate:$VERSION"`
     *
     * Algorithm:
     * - For each part of the string:
     *  - Static Text (`GrStringContent`):
     *    - Check for proper colon placement.
     *    - Split text by colons and add valid segments to the list.
     *  - Interpolated Expression (`GrStringInjection`):
     *    - Ensure it follows a colon.
     *    - Convert the expression to a coordinate part and add it to the list.
     */
    private fun GrString.parseAsInterpolatedStringDependency(
        configuration: String,
        isTransitiveByClosureBlock: Boolean?,
    ): Dependency? {
        val parts = mutableListOf<Dependency.DependencyCoordinatePart>()
        var lastCharWasColon = true
        for (contentPart in allContentParts) {
            when (contentPart) {
                is GrStringContent -> {
                    var value = contentPart.value ?: return null
                    if (value == ":") {
                        if (lastCharWasColon) return null
                        lastCharWasColon = true
                    } else {
                        if (!lastCharWasColon && !value.startsWith(":")) return null
                        value = value.removePrefix(":")
                        lastCharWasColon = value.endsWith(":")
                        value = value.removeSuffix(":")
                        parts += value.split(":").map { Dependency.DependencyCoordinatePart.StringPart(it) }
                    }
                }

                is GrStringInjection -> {
                    if (!lastCharWasColon) return null
                    lastCharWasColon = false
                    val expression = contentPart.expression
                        ?: contentPart.closableBlock?.statements?.singleOrNull() as? GrExpression
                        ?: return null
                    parts += expression.asCoordinatePart() ?: return null
                }

                else -> return null
            }
        }
        if (parts.size != 3) return null

        if (lastCharWasColon) return null
        return Dependency(
            configuration = configuration,
            group = parts[0],
            name = parts[1],
            version = parts[2],
            transitive = isTransitiveByClosureBlock,
            format = Dependency.Format.SingleString
        )
    }

    /**
     * Converts a `GrExpression` into a `Dependency.CoordinatePart`, if possible.
     *
     * - For `GrLiteral`, returns a `StringPart` with its string value.
     * - For simple (unqualified) `GrReferenceExpression`, returns a `SimpleReferencePart` with its name.
     * - Returns `null` if the expression cannot be converted.
     */
    private fun GrExpression.asCoordinatePart(): Dependency.DependencyCoordinatePart? {
        return when (this) {
            is GrLiteral -> {
                val value = stringValue() ?: return null
                Dependency.DependencyCoordinatePart.StringPart(value)
            }

            is GrReferenceExpression -> {
                if (isQualified) return null
                val name = referenceName ?: return null
                Dependency.DependencyCoordinatePart.SimpleReferencePart(name)
            }

            else -> null
        }
    }

    /**
     * Converts a string representing a dependency of the format `group:name:version` to a dependency.
     */
    private fun String.parseAsStringLiteralDependency(
        configuration: String,
        isTransitiveByClosureBlock: Boolean?,
    ): Dependency? {
        val parts = split(':')
        if (parts.size != 3) return null
        return Dependency(
            configuration = configuration,
            group = Dependency.DependencyCoordinatePart.StringPart(parts[0]),
            name = Dependency.DependencyCoordinatePart.StringPart(parts[1]),
            version = Dependency.DependencyCoordinatePart.StringPart(parts[2]),
            transitive = isTransitiveByClosureBlock,
            format = Dependency.Format.SingleString
        )
    }
}


private object DependencyRenderer {
    fun render(dependencyNodes: List<DependencyNode>): String {
        return dependencyNodes.joinToString(separator = "\n") { node ->
            when (node) {
                is DependencyNode.Comment -> node.comment.text
                is Dependency -> renderDependencyAsKotlin(node)
            }
        }
    }

    private fun renderDependencyAsKotlin(dependency: Dependency): String {
        val call = when (val format = dependency.format) {
            Dependency.Format.SingleString -> buildString {
                append(dependency.configuration)
                append("(\"")
                append(dependency.group.render(format))
                append(":")
                append(dependency.name.render(format))
                append(":")
                append(dependency.version.render(format))
                append("\")")
            }

            Dependency.Format.NamedArguments -> buildString {
                append(dependency.configuration)
                append("(group = ")
                append(dependency.group.render(format))
                append(", name = ")
                append(dependency.name.render(format))
                append(", version = ")
                append(dependency.version.render(format))
                append(")")
            }
        }
        return when {
            dependency.transitive == null -> call
            else -> """$call { isTransitive = ${dependency.transitive} }""".trimMargin()
        }
    }

    private fun Dependency.DependencyCoordinatePart.render(format: Dependency.Format): String? {
        return when (this) {
            is Dependency.DependencyCoordinatePart.StringPart -> when (format) {
                Dependency.Format.SingleString -> value
                Dependency.Format.NamedArguments -> "\"$value\""
            }

            is Dependency.DependencyCoordinatePart.SimpleReferencePart -> when (format) {
                Dependency.Format.SingleString -> "\${$name}"
                Dependency.Format.NamedArguments -> name
            }
        }
    }
}

private sealed interface DependencyNode {
    data class Comment(val comment: PsiComment) : DependencyNode

    data class Dependency(
        val configuration: String,
        val group: DependencyCoordinatePart,
        val name: DependencyCoordinatePart,
        val version: DependencyCoordinatePart,
        /**
         * `null` if `transitive` flag is not defined in the original Groovy code
         */
        val transitive: Boolean?,
        val format: Format,
    ) : DependencyNode {
        init {
            require(configuration in allDependenciesConfigurations) {
                "Unknown configuration: $configuration"
            }
        }

        /**
         * A part of a dependency coordinate which can be one of: group, name, or version.
         *
         * It can be either a simple string [StringPart] or a simple non-qualified reference like `VER` [SimpleReferencePart].
         */
        sealed interface DependencyCoordinatePart {
            class StringPart(val value: String) : DependencyCoordinatePart
            class SimpleReferencePart(val name: String) : DependencyCoordinatePart
        }

        enum class Format {
            SingleString, NamedArguments
        }
    }
}


private val allDependenciesConfigurations: Set<String> = setOf(
    "implementation",
    "api",
    "compileOnly",
    "runtimeOnly",
    "testImplementation",
    "testApi",
    "testCompileOnly",
    "testRuntimeOnly",
)