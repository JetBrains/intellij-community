package ru.adelf.idea.dotenv.rust

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsCallExpr
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.RsLiteralKind
import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.psi.RsValueArgumentList
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.kind
import org.rust.lang.core.resolve.KnownItems
import org.rust.lang.core.resolve.knownItems

internal object RustPsiHelper {
    private suspend fun SequenceScope<RsFunction>.yieldNotNull(value: RsFunction?) {
        if (value != null) yield(value)
    }

    private fun rustEnvFunctions(knownItems: KnownItems) = sequence {
        yieldNotNull(knownItems.findItem("std::env::var"))
        yieldNotNull(knownItems.findItem("std::env::var_os"))
        yieldNotNull(knownItems.findItem("std::env::set_var"))
        yieldNotNull(knownItems.findItem("std::env::remove_var"))
        yieldNotNull(knownItems.findItem("dotenv::var", isStd = false))
        yieldNotNull(knownItems.findItem("dotenvy::var", isStd = false))
    }

    fun findEnvLiteral(call: RsCallExpr): RsLitExpr? {
        val pathExpr = call.expr as? RsPathExpr ?: return null
        val function = pathExpr.path.reference?.resolve() as? RsFunction ?: return null
        if (function !in rustEnvFunctions(call.knownItems)) return null

        return call.valueArgumentList.exprList.firstOrNull() as? RsLitExpr
    }

    fun findWrappingEnvLiteral(element: PsiElement): RsLitExpr? {
        val literal = element.ancestorOrSelf<RsLitExpr>() ?: return null
        findWrappingCall(literal)?.let { call ->
            if (literal == findEnvLiteral(call)) return literal
        }

        return null
    }

    fun getStringValue(literal: RsLitExpr): String? {
        val string = literal.kind as? RsLiteralKind.String ?: return null
        if (string.isByte || string.isCStr) return null
        return string.value
    }

    private fun findWrappingCall(expr: RsLitExpr): RsCallExpr? {
        val argumentList = expr.parent as? RsValueArgumentList ?: return null
        return argumentList.parent as? RsCallExpr
    }
}
