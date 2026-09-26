package ru.adelf.idea.dotenv.rust

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.rust.lang.RsFileType
import org.rust.lang.core.psi.RsCallExpr
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsRecursiveVisitor
import ru.adelf.idea.dotenv.api.EnvironmentVariablesUsagesProvider
import ru.adelf.idea.dotenv.models.KeyUsagePsiElement

private class RustEnvironmentCallsVisitor : RsRecursiveVisitor() {
    private val collectedItems = HashSet<KeyUsagePsiElement>()

    override fun visitCallExpr(o: RsCallExpr) {
        val literal = RustPsiHelper.findEnvLiteral(o)
        val key = literal?.let(RustPsiHelper::getStringValue) ?: return
        collectedItems.add(KeyUsagePsiElement(key, literal))
        super.visitCallExpr(o)
    }

    fun getCollectedItems(): Collection<KeyUsagePsiElement> = collectedItems
}

internal class RustEnvironmentVariablesUsagesProvider : EnvironmentVariablesUsagesProvider {
    override fun acceptFile(file: VirtualFile): Boolean = file.fileType == RsFileType

    override fun getUsages(psiFile: PsiFile): Collection<KeyUsagePsiElement> {
        if (psiFile !is RsFile) return emptyList()

        val visitor = RustEnvironmentCallsVisitor()
        psiFile.acceptChildren(visitor)
        return visitor.getCollectedItems()
    }
}
