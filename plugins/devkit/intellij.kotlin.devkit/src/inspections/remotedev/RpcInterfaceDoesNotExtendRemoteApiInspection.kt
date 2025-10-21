// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.remotedev

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle.message
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtVisitorVoid

class RpcInterfaceDoesNotExtendRemoteApiInspection : LocalInspectionTool() {

  private val rpcAnnotationFqn = FqName("fleet.rpc.Rpc")

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!DevKitInspectionUtil.isAllowed(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
    return object : KtVisitorVoid() {
      override fun visitClass(klass: KtClass) {
        if (!klass.isAnnotatedWithRpc()) return
        analyze(klass) {
          val symbol = klass.symbol
          if (symbol is KaClassSymbol) {
            val defaultType = symbol.defaultType
            if (doesNotExtendRemoteApi(defaultType)) {
              val classNameElement = klass.nameIdentifier ?: return
              holder.registerProblem(classNameElement, message("inspection.remote.dev.rpc.interface.does.not.extend.remote.api.name"))
            }
          }
        }
      }

      private fun KtClass.isAnnotatedWithRpc(): Boolean {
        val declaration = this
        return analyze(declaration) {
          declaration.symbol.annotations.any { it.classId?.asSingleFqName() == rpcAnnotationFqn }
        }
      }

      private fun KaSession.doesNotExtendRemoteApi(defaultType: KaType): Boolean {
        return defaultType.allSupertypes.none { superType ->
          val superTypeName = superType.symbol?.classId?.asSingleFqName()?.asString()
          superTypeName == "fleet.rpc.RemoteApi"
        }
      }
    }
  }
}
