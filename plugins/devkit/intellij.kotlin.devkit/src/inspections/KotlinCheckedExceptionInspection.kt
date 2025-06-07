// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.ThrowsChecked
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.findParentOfType
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.contracts.description.KaContractCallsInPlaceContractEffectDeclaration
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaAnnotatedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.contracts.description.EventOccurrencesRange
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversions
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.addIfNotNull

class KotlinCheckedExceptionInspection : AbstractKotlinInspection() {
  private class UncheckedExceptionsCachedValueProvider(private val element: KtCallExpression) : CachedValueProvider<Collection<FqName>> {
    override fun compute(): CachedValueProvider.Result<Collection<FqName>>? {
      // Resolves the function designated by `element` and collects contents of `@ThrowsChecked` annotations.
      val exceptionsToCheck: MutableCollection<FqName> = analyze(element) {
        val call = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return@analyze hashSetOf()
        val annotatedSymbol = call.symbol as? KaAnnotatedSymbol ?: return@analyze hashSetOf()
        exceptionsToCheck(annotatedSymbol.annotations.mapNotNull { it.psi as? KtAnnotationEntry }).toHashSet()
      }

      // If the callable is a variable with a function type, collects `@ThrowsChecked` from the variable type.
      run {
        val calleeExpression = element.calleeExpression as? KtNameReferenceExpression ?: return@run
        analyze(calleeExpression) {
          for (call in (calleeExpression.resolveToCall() ?: return@analyze).calls) {
            if (call is KaCallableMemberCall<*, *>) {
              val annotations = call.partiallyAppliedSymbol.signature.returnType.annotations
              exceptionsToCheck.addAll(exceptionsToCheck(annotations.mapNotNull { it.psi as? KtAnnotationEntry }))
            }
          }
        }
      }

      if (exceptionsToCheck.isEmpty()) {
        return null
      }

      // Simplifies checking of cases like `catch (BaseException)`, when the checked exception is an instance of `BaseException`.
      val exceptionsToCheckWithSuperclasses: MutableMap<FqName, Set<FqName>> =
        exceptionsToCheck.associateWithTo(mutableMapOf()) { fqName -> allSuperClasses(element, fqName) }

      val cacheDependencies = mutableListOf<PsiElement>()
      var currentNode: PsiElement? = element
      while (true) {
        when (currentNode) {
          null, is PsiFile -> break

          is KtLambdaExpression -> {
            when (val checkResult = currentNode.executedInPlaceByCallable()) {
              LambdaCheckResult.RethrowsInPlace -> {
                // It's known that there are no try-catch blocks in the lambda.
                // Skip it.
              }

              is LambdaCheckResult.HandlesExceptions ->
                exceptionsToCheckWithSuperclasses.values.removeAll { superClasses ->
                  checkResult.exceptions.any { it in superClasses }
                }

              LambdaCheckResult.Unknown ->
                break
            }
          }

          is KtDeclarationWithBody -> {
            if (currentNode.parent !is KtLambdaExpression) {
              // It's likely a function or a class constructor.
              val throws = exceptionsToCheck(currentNode.annotationEntries)
              if (throws.isNotEmpty()) {
                exceptionsToCheckWithSuperclasses.values.removeAll { superClasses ->
                  superClasses.any { superClass -> superClass in throws }
                }
              }
              break
            }
          }

          is KtTryExpression -> {
            // Forgets about checked exceptions that are handled in a `catch`-block.
            // Notice that the inspection doesn't check if a `catch`-block rethrows the checked exception, there is no warning.
            // It's intentional.
            // The goal of the inspection is to remind about the necessity of handling exceptions
            // but not prevent any unexpected exception in compile time.
            for (catchClause in currentNode.catchClauses) {
              catchClause.accept(object : PsiElementVisitor() {
                override fun visitElement(element: PsiElement) {
                  if (element is KtNameReferenceExpression) {
                    val clsFqName: FqName =
                      (element.reference?.resolve() as? KtClass)?.fqName?.maybeToKotlinFqName()
                      ?: return
                    exceptionsToCheckWithSuperclasses.values.removeAll { superClasses ->
                      clsFqName in superClasses
                    }
                  }
                  else {
                    ProgressIndicatorProvider.checkCanceled()
                    element.acceptChildren(this)
                  }
                }
              })
            }
          }
        }

        cacheDependencies.add(currentNode)
        currentNode = currentNode.parent
      }

      return CachedValueProvider.Result.create(exceptionsToCheckWithSuperclasses.keys, *cacheDependencies.toTypedArray())
    }
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {
    override fun visitElement(element: PsiElement) {
      if (element !is KtCallExpression) return

      val uncheckedExceptions =
        CachedValuesManager.getManager(element.project).getCachedValue(element, UncheckedExceptionsCachedValueProvider(element))

      if (!uncheckedExceptions.isNullOrEmpty()) {
        val quickFixes = mutableListOf<LocalQuickFix>()

        val closestAnnotated = element.findParentOfType<KtDeclarationWithBody>(strict = false)

        if (closestAnnotated != null) {
          quickFixes += AddTryCatchQuickFix()
        }

        quickFixes += AddAnnotationQuickFix()

        holder.registerProblem(
          element,
          DevKitKotlinBundle.message("inspection.checked.exceptions.message", uncheckedExceptions.map { it.toString() }.sorted().joinToString()),
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
          *quickFixes.toTypedArray(),
        )
      }
    }
  }

  private class AddAnnotationQuickFix() : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName(): @IntentionFamilyName String = DevKitKotlinBundle.message("intention.checked.exceptions.add.annotation")

    @OptIn(KaExperimentalApi::class)
    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
      // TODO Use the power of ModPsiUpdater.

      if (element !is KtCallExpression) {
        return
      }
      val whereAddAnnotationTo = run {
        var candidate: PsiElement? = element.parent
        while (candidate != null) {
          when (candidate) {
            is KtNamedFunction -> {
              // It's something like `fun foobar()`, the annotation must be added before `fun`.
              return@run candidate
            }

            is KtFunctionLiteral -> {
              val lambdaExpression = candidate.parent as? KtLambdaExpression
              val property = lambdaExpression?.parent as? KtProperty
              if (property != null) {
                if (property.typeReference == null) {
                  val typeInfo = analyze(property) {
                    CallableReturnTypeUpdaterUtils.getTypeInfo(property)
                  }

                  CallableReturnTypeUpdaterUtils.updateType(
                    declaration = property,
                    typeInfo = typeInfo,
                    project = project,
                    editor = null,
                  )
                }
                // It's something like `val myLambda: () -> Foobar = { ... }`, the annotation must be added to the type.
                return@run property.typeReference ?: return
              }
            }
          }
          candidate = candidate.parent
        }
        return
      }
      val uncheckedExceptions =
        CachedValuesManager.getManager(element.project).getCachedValue(element, UncheckedExceptionsCachedValueProvider(element))

      for (uncheckedException in uncheckedExceptions) {
        whereAddAnnotationTo.addAnnotation(
          annotationClassId = ClassId.fromString(ThrowsChecked::class.qualifiedName!!),
          annotationInnerText = "${uncheckedException}::class",
          searchForExistingEntry = true,
        )
      }
    }
  }

  private class AddTryCatchQuickFix() : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName(): @IntentionFamilyName String = DevKitKotlinBundle.message("intention.checked.exceptions.surround.with.try.catch")

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
      // TODO Use the power of ModPsiUpdater.
      if (element !is KtCallExpression) return
      val uncheckedExceptions =
        CachedValuesManager.getManager(element.project).getCachedValue(element, UncheckedExceptionsCachedValueProvider(element))
      if (uncheckedExceptions.isNullOrEmpty()) return

      val expressionToSurround: KtElement = run {
        var expressionToSurround: KtElement? = element
        while (true) {
          when (expressionToSurround) {
            null ->
              return

            is KtBlockExpression ->
              break

            else -> {
              expressionToSurround = expressionToSurround.parent as? KtElement
            }
          }
        }
        expressionToSurround
      }

      var tryCatchElement: KtTryExpression = KtPsiFactory.contextual(element).createExpression(buildString {
        append("try {\n}")
        for (uncheckedException in uncheckedExceptions) {
          append("catch (err: ${uncheckedException.asString()}) {\nTODO(\"Unhandled exception \$err\")\n}")
        }
      }) as KtTryExpression
      val codeStyleManager = CodeStyleManager.getInstance(project)
      codeStyleManager.reformat(tryCatchElement)

      val cutFrom: PsiElement? =
        generateSequence(expressionToSurround.firstChild) { it.nextSibling }
          .find { e -> e !is PsiWhiteSpace && e !is LeafPsiElement }

      val cutUntilInclusive: PsiElement? =
        generateSequence(expressionToSurround.lastChild) { it.prevSibling }
          .takeWhile { it != cutFrom }
          .find { it !is PsiWhiteSpace && it !is LeafPsiElement }

      tryCatchElement =
        when {
          cutFrom?.prevSibling != null -> expressionToSurround.addAfter(tryCatchElement, cutFrom.prevSibling)
          expressionToSurround.firstChild != null -> expressionToSurround.addBefore(tryCatchElement, expressionToSurround.firstChild)
          else -> expressionToSurround.add(tryCatchElement)
        } as KtTryExpression

      val elementsToMoveIntoTryBlock: List<PsiElement> =
        if (cutFrom != null && cutUntilInclusive != null) {
          val elements = generateSequence(cutFrom) { it.nextSibling }
            .takeWhile { it.prevSibling != cutUntilInclusive }
            .map { it.copy() }
            .toList()
          expressionToSurround.deleteChildRange(cutFrom, cutUntilInclusive)
          elements
        }
        else {
          listOf()
        }

      for (child in tryCatchElement.children) {
        when (child) {
          is KtCatchClause -> {
            ShortenReferencesFacility.getInstance().shorten(child)
          }
          is KtBlockExpression -> {
            for (element in elementsToMoveIntoTryBlock) {
              child.addBefore(element, child.lastChild.prevSibling)
            }
            updater.moveCaretTo(child)
          }
        }
      }
      codeStyleManager.reformat(tryCatchElement, true)
    }
  }
}

/** Converts things like [java.lang.Integer] to [kotlin.Int]. */
private fun FqName.maybeToKotlinFqName(): FqName =
  JavaToKotlinClassMap.mapJavaToKotlin(this)?.asSingleFqName()
  ?: this

/** Parses a list of annotations, extracts all classes from all `@ThrowChecked`, return fully qualified names of the extracted classes. */
private fun exceptionsToCheck(annotations: Collection<KtAnnotationEntry>): Collection<FqName> {
  val result = mutableListOf<FqName>()
  for (annotation in annotations) {
    analyze(annotation) annotationAnalyze@{
      val constructorCall =
        annotation.resolveToCall()?.successfulConstructorCallOrNull()
        ?: return@annotationAnalyze
      if (constructorCall.symbol.containingClassId?.asSingleFqName() == FqName(ThrowsChecked::class.qualifiedName!!)) {
        for (valueArgument in annotation.valueArguments) {
          val cle =
            valueArgument.getArgumentExpression() as? KtClassLiteralExpression
            ?: continue
          val fqName = analyze(cle) {
            (cle.receiverType as? KaClassType)?.classId?.asSingleFqName()
          }
          if (fqName != null) {
            result.add(fqName)
          }
        }
      }
    }
  }
  return result
}

private fun allSuperClasses(elementForScope: PsiElement, exceptionFqName: FqName): Set<FqName> {
  val superClassSet = mutableSetOf(exceptionFqName)
  var psiClass =
    JavaPsiFacade.getInstance(elementForScope.project)
      .findClass(exceptionFqName.asString(), elementForScope.resolveScope)
      ?.superClass
  while (psiClass != null) {
    superClassSet.addIfNotNull(psiClass.qualifiedName?.let(::FqName)?.maybeToKotlinFqName())
    psiClass = psiClass.superClass
  }
  return superClassSet
}

private sealed interface LambdaCheckResult {
  object RethrowsInPlace : LambdaCheckResult
  class HandlesExceptions(val exceptions: Collection<FqName>) : LambdaCheckResult
  object Unknown : LambdaCheckResult
}

@OptIn(KaExperimentalApi::class)
private fun KtLambdaExpression.executedInPlaceByCallable(): LambdaCheckResult {
  val callExpression = when (val parent = parent) {
    is KtLambdaArgument -> {
      // Something like `run { ... }`
      parent.parent
    }

    is KtValueArgument -> {
      // Something like `run(body = { ... })`
      parent.parent?.parent
    }

    else -> null
  }

  if (callExpression !is KtCallExpression) {
    return LambdaCheckResult.Unknown
  }

  analyze(callExpression) {
    val call = callExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return@analyze
    val fqName = call.symbol.callableId?.asSingleFqName() ?: return@analyze

    // TODO Optimize
    for (knownRethrowingFunctions in CallChainConversions.conversionsList) {
      if (fqName == knownRethrowingFunctions.firstFqName || fqName == knownRethrowingFunctions.secondFqName) {
        return LambdaCheckResult.RethrowsInPlace
      }
    }
  }

  val callerAnnotations: Collection<KtAnnotationEntry> = analyze(callExpression) {
    val call = callExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return@analyze emptyList()
    val callerAnnotations = call.symbol.annotations.mapNotNull { it.psi as? KtAnnotationEntry }

    val functionSymbol = call.symbol as? KaNamedFunctionSymbol
    val callEffect = functionSymbol?.contractEffects
      ?.filterIsInstance<KaContractCallsInPlaceContractEffectDeclaration>()
      ?.singleOrNull()

    when (callEffect?.occurrencesRange) {
      EventOccurrencesRange.AT_MOST_ONCE,
      EventOccurrencesRange.EXACTLY_ONCE,
      EventOccurrencesRange.AT_LEAST_ONCE,
      EventOccurrencesRange.MORE_THAN_ONCE,
        -> {
        // TODO Check what exceptions the decorator handles.
        return LambdaCheckResult.RethrowsInPlace
      }

      EventOccurrencesRange.ZERO,
      EventOccurrencesRange.UNKNOWN,
      null,
        -> Unit
    }

    callerAnnotations
  }

  val parameterAnnotations: Collection<KtAnnotationEntry> = when (val calleeExpression = callExpression.calleeExpression) {
    is KtNameReferenceExpression -> analyze(calleeExpression) {
      val signature = calleeExpression.resolveToCall()?.successfulFunctionCallOrNull()?.argumentMapping[this@executedInPlaceByCallable]
      if (signature != null) {
        val symbol = signature.symbol
        symbol.returnType.annotations.mapNotNull { it.psi as? KtAnnotationEntry }
      }
      else emptyList()
    }
    else -> emptyList()
  }

  run {
    val exceptionsToCheck =
      exceptionsToCheck(parameterAnnotations)
        .plus(exceptionsToCheck(callerAnnotations))
        .toSet()
    if (exceptionsToCheck.isNotEmpty()) {
      return LambdaCheckResult.HandlesExceptions(exceptionsToCheck)
    }
  }

  return LambdaCheckResult.Unknown
}