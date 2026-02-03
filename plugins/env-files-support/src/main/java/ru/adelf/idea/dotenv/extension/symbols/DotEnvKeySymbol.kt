package ru.adelf.idea.dotenv.extension.symbols

import com.intellij.dotenv.icons.DotenvIcons
import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageHandler
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.api.RenameTarget

class DotEnvKeySymbol(
    val name: @NlsSafe String,
    val file: PsiFile,
    val rangeInFile: TextRange,
) : Symbol, NavigationTarget, SearchTarget, RenameTarget {

    override fun createPointer(): Pointer<DotEnvKeySymbol> {
        return Pointer.fileRangePointer(file, rangeInFile) { restoredFile, restoredRange ->
            DotEnvKeySymbol(name, file, rangeInFile)
        }
    }

    override fun computePresentation(): TargetPresentation = TargetPresentation.builder(name)
        .locationText(
            "${file.presentation?.presentableText ?: file.name}:${file.fileDocument.getLineNumber(rangeInFile.startOffset)}",
            file.getIcon(Iconable.ICON_FLAG_VISIBILITY)
        )
        .icon(DotenvIcons.Env)
        .presentation()

    override fun navigationRequest(): NavigationRequest? = NavigationRequest.sourceNavigationRequest(file, rangeInFile)

    override fun presentation(): TargetPresentation = TargetPresentation.builder(name).presentation()

    override fun equals(other: Any?): Boolean = (other as? DotEnvKeySymbol)?.let { it.name == name } == true

    override fun hashCode(): Int = name.hashCode()

    override val usageHandler: UsageHandler
        get() = UsageHandler.createEmptyUsageHandler(name)

    override val maximalSearchScope: SearchScope
        get() = GlobalSearchScope.projectScope(file.project)

    override val targetName: String
        get() = name

    fun declarationUsage(): DotEnvKeyDeclarationUsage = DotEnvKeyDeclarationUsage(file, rangeInFile)

}