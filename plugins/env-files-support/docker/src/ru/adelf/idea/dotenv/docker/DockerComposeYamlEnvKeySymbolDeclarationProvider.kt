package ru.adelf.idea.dotenv.docker

import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolDeclarationProvider
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import ru.adelf.idea.dotenv.extension.symbols.DotEnvKeySymbolDeclaration

class DockerComposeYamlEnvKeySymbolDeclarationProvider : PsiSymbolDeclarationProvider {

    private val recognizer = Regex("\\s*([!\\s]+?):")

    override fun getDeclarations(element: PsiElement, offsetInElement: Int): Collection<PsiSymbolDeclaration> {
        return (element as? YAMLKeyValue)?.let { keyValue ->
            if ("environment" == keyValue.getKeyText()) {
                when (keyValue.lastChild) {
                    is YAMLSequence -> extractKeysFromScalarSequence(keyValue.lastChild as YAMLSequence)
                    is YAMLMapping -> extractKeysFromMapping(keyValue.lastChild as YAMLMapping)
                    else -> null
                }
            }
            else null
        } ?: emptyList()
    }

    private fun extractKeysFromMapping(mapping: YAMLMapping): List<DotEnvKeySymbolDeclaration> {
        return mapping.keyValues.mapNotNull { keyValue ->
            keyValue.key?.let {
                DotEnvKeySymbolDeclaration(it)
            }
        }
    }

    private fun extractKeysFromScalarSequence(sequence: YAMLSequence): List<DotEnvKeySymbolDeclaration> {
        return sequence.items.mapNotNull { item ->
            (item as? YAMLScalar)?.let(::extractKeyFromScalar)
        }
    }

    private fun extractKeyFromScalar(scalar: YAMLScalar): DotEnvKeySymbolDeclaration? {
        return recognizer.find(scalar.textValue)?.range?.let { range ->
            DotEnvKeySymbolDeclaration(
                scalar,
                TextRange(
                    scalar.textRange.startOffset + range.start,
                    scalar.textRange.startOffset + range.endInclusive + 1
                )
            )
        }
    }

}
