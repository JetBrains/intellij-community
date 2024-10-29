// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.*
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.group
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.annotationRequest
import com.intellij.lang.jvm.actions.createAddAnnotationActions
import com.intellij.psi.PsiMethod
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.map2Array
import com.siyeh.ig.callMatcher.CallMatcher
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.util.*

internal class ThreadingConcurrencyInspection(
  @JvmField var requiresReadLockInsideRequiresEdt: Boolean = false,
  @JvmField var requiresWriteLockInsideRequiresEdt: Boolean = false,
  @JvmField var checkMissingAnnotations: Boolean = false)
  : DevKitUastInspectionBase(UMethod::class.java) {

  override fun getOptionsPane(): OptPane {
    return OptPane.pane(
      group(DevKitBundle.message("inspection.threading.concurrency.option.group.inside.requires.edt"),
            checkbox("requiresReadLockInsideRequiresEdt",
                     DevKitBundle.message("inspection.threading.concurrency.option.group.inside.requires.edt.check.requires.read.lock")),
            checkbox("requiresWriteLockInsideRequiresEdt",
                     DevKitBundle.message("inspection.threading.concurrency.option.group.inside.requires.edt.check.requires.write.lock"))
      ),
      checkbox("checkMissingAnnotations",
               DevKitBundle.message("inspection.threading.concurrency.option.check.missing.annotations.methods"))
    )
  }

  // skip if ancient platform version
  override fun isAllowed(holder: ProblemsHolder): Boolean {
    return DevKitInspectionUtil.isAllowedIncludingTestSources(holder.file) &&
           DevKitInspectionUtil.isClassAvailable(holder, RequiresEdt::class.java.canonicalName)
  }

  override fun checkMethod(method: UMethod, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    val uastBody = method.uastBody ?: return null

    val problemsHolder = createProblemsHolder(method, manager, isOnTheFly)

    val checkMissingAnnotations = checkMissingAnnotations &&
                                  method.javaPsi.hasModifier(JvmModifier.PUBLIC) &&
                                  method.javaPsi.findSuperMethods().isEmpty()
    uastBody.accept(ThreadingVisitor(problemsHolder, method.getThreadingStatuses(), checkMissingAnnotations))

    return problemsHolder.resultsArray
  }

  private inner class ThreadingVisitor(private val problemsHolder: ProblemsHolder,
                                       private val methodThreadingStatus: Set<ThreadingStatus>,
                                       private val checkMissingAnnotations: Boolean) : AbstractUastVisitor() {

    // do not recurse
    override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
      return true
    }

    // do not recurse
    override fun visitObjectLiteralExpression(node: UObjectLiteralExpression): Boolean {
      return true
    }

    override fun visitCallExpression(node: UCallExpression): Boolean {
      val resolvedMethod = node.resolveToUElementOfType<UMethod>() ?: return true

      val threadingStatus = resolvedMethod.getThreadingStatuses()
      if (threadingStatus.isEmpty()) {
        return true
      }

      // check violations, skip missing if found
      if (!methodThreadingStatus.isEmpty()) {
        if (skipCallExpression(node)) {
          return true
        }

        if (checkViolations(node, threadingStatus)) {
          return true
        }
      }

      // check missing
      if (checkMissingAnnotations && !methodThreadingStatus.containsAll(threadingStatus)) {
        checkMissingAnnotations(node, threadingStatus)
      }

      return false
    }

    private val EVENT_DISPATCHER = CallMatcher.instanceCall(EventDispatcher::class.java.canonicalName,
                                                            "getMulticaster")

    private fun skipCallExpression(uCallExpression: UCallExpression): Boolean {
      val receiver = uCallExpression.receiver as? UQualifiedReferenceExpression ?: return false

      val selector = receiver.selector as? UResolvable ?: return false
      val psiMethod = selector.resolve() as? PsiMethod ?: return false
      return EVENT_DISPATCHER.methodMatches(psiMethod)
    }

    private fun checkMissingAnnotations(node: UCallExpression,
                                        threadingStatus: Set<ThreadingStatus>): Boolean {
      require(threadingStatus.isNotEmpty())

      // collect all ThreadingStatus as fixes
      val methodToAnnotate = node.getParentOfType(UMethod::class.java)!!
      val allFixes = mutableListOf<IntentionAction>()
      for (value in threadingStatus) {
        allFixes += createAddAnnotationActions(methodToAnnotate.javaPsi, annotationRequest(value.annotationFqn))
      }

      val methodMissingThreadingStatus = threadingStatus.toMutableSet()
      methodMissingThreadingStatus.removeAll(methodThreadingStatus)

      if (methodMissingThreadingStatus.size == 1) {
        registerProblem(node, IntentionWrapper.wrapToQuickFixes(allFixes, problemsHolder.file).toTypedArray(),
                        "inspection.threading.concurrency.violation.unannotated.method.contains.call",
                        methodMissingThreadingStatus.first().getDisplayName())
      }
      else {
        registerProblem(node, IntentionWrapper.wrapToQuickFixes(allFixes, problemsHolder.file).toTypedArray(),
                        "inspection.threading.concurrency.violation.unannotated.method.contains.call.multiple.annotations")
      }

      return true
    }

    /**
     * @return `true` if a violation was found
     */
    private fun checkViolations(node: UCallExpression,
                                threadingStatus: Set<ThreadingStatus>): Boolean {
      require(methodThreadingStatus.isNotEmpty())
      require(threadingStatus.isNotEmpty())
      if (checkViceVersa(node, EnumSet.of(ThreadingStatus.REQUIRES_EDT),
                         threadingStatus,
                         EnumSet.of(ThreadingStatus.REQUIRES_BGT))) {
        return true
      }


      // disabled by default
      if (requiresReadLockInsideRequiresEdt &&
          checkSimple(node, EnumSet.of(ThreadingStatus.REQUIRES_EDT), threadingStatus, EnumSet.of(ThreadingStatus.REQUIRES_RL))) {
        return true
      }
      if (requiresWriteLockInsideRequiresEdt &&
          checkSimple(node, EnumSet.of(ThreadingStatus.REQUIRES_EDT), threadingStatus, EnumSet.of(ThreadingStatus.REQUIRES_WL))) {
        return true
      }


      if (checkSimple(node, EnumSet.of(ThreadingStatus.REQUIRES_RL), threadingStatus, EnumSet.of(ThreadingStatus.REQUIRES_WL))) {
        return true
      }


      return checkViceVersa(node, EnumSet.of(ThreadingStatus.REQUIRES_RL_ABSENCE),
                            threadingStatus,
                            EnumSet.of(ThreadingStatus.REQUIRES_RL, ThreadingStatus.REQUIRES_WL))
    }

    private fun checkSimple(node: UCallExpression,
                            searchForMethodStatus: Set<ThreadingStatus>,
                            threadingStatus: Set<ThreadingStatus>,
                            searchForCallStatus: Set<ThreadingStatus>): Boolean {
      for (methodStatus in searchForMethodStatus) {
        if (methodThreadingStatus.contains(methodStatus)) {
          for (callStatus in searchForCallStatus) {
            if (threadingStatus.contains(callStatus)) {
              registerThreadingStatusProblem(node, "inspection.threading.concurrency.violation.call.inside.method", callStatus,
                                             methodStatus)
              return true
            }
          }
        }
      }
      return false
    }

    private fun checkViceVersa(node: UCallExpression,
                               searchForMethodStatus: Set<ThreadingStatus>,
                               threadingStatus: Set<ThreadingStatus>,
                               searchForCallStatus: Set<ThreadingStatus>): Boolean {
      if (checkSimple(node, searchForMethodStatus, threadingStatus, searchForCallStatus)) {
        return true
      }
      return checkSimple(node, searchForCallStatus, threadingStatus, searchForMethodStatus)
    }

    private fun registerThreadingStatusProblem(uElement: UCallExpression,
                                               @PropertyKey(resourceBundle = DevKitBundle.BUNDLE) messageKey: String,
                                               vararg params: ThreadingStatus) {
      registerProblem(uElement, LocalQuickFix.EMPTY_ARRAY, messageKey, *params.map2Array { it.getDisplayName() })
    }

    private fun registerProblem(uCallExpression: UCallExpression,
                                quickFixes: Array<LocalQuickFix> = LocalQuickFix.EMPTY_ARRAY,
                                @PropertyKey(resourceBundle = DevKitBundle.BUNDLE) messageKey: String,
                                vararg params: Any) {
      problemsHolder.registerUProblem(uCallExpression, DevKitBundle.message(messageKey, *params), *quickFixes)
    }
  }

}
