package org.jetbrains.plugins.feature.suggester.defaultSuggesters

import org.jetbrains.plugins.feature.suggester._
import org.jetbrains.plugins.feature.suggester.changes._
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.codeInsight.ExpectedTypesProvider
import org.jetbrains.plugins.feature.suggester.changes.ChildReplacedAction
import org.jetbrains.plugins.feature.suggester.changes.ChildAddedAction

/**
 * @author Alefas
 * @since 23.05.13
 */
class SecondToArrayCompletionSuggester extends FeatureSuggester {
  val POPUP_MESSAGE = "Why not to use second smart completion for 'toArray' method (double Ctrl + Shift + Space)"

  def getId: String = "Second toArray completion suggester"

  def getSuggestion(actions: List[UserAction], anActions: List[UserAnAction]): Suggestion = {
    actions.last match {
      case ChildAddedAction(_, call: PsiMethodCallExpression) =>
        if (checkMethodCall(call)) {
          if (call.getArgumentList.getExpressions.size > 0) return NoSuggestion //todo: hack not to suggest in wrong place
          return SuggestingUtil.createSuggestion(Some("editing.completion.second.smarttype.toar"), POPUP_MESSAGE)
        }
      case ChildReplacedAction(_, call: PsiMethodCallExpression, oldChild) =>
        if (checkMethodCall(call)) {
          if (call.getArgumentList.getExpressions.size > 0) return NoSuggestion //todo: hack not to suggest in wrong place
          return SuggestingUtil.createSuggestion(Some("editing.completion.second.smarttype.toar"), POPUP_MESSAGE)
        }
      case _ =>
    }
    NoSuggestion
  }

  override def needToClearLookup(): Boolean = true

  private def checkMethodCall(call: PsiMethodCallExpression): Boolean = {
    val ref = call.getMethodExpression
    if (ref.getReferenceName != "toArray") return false
    ref.getQualifierExpression match {
      case ref: PsiReferenceExpression if ref.getQualifierExpression == null   => //do not check for complex qualifiers
      case _ => return false
    }
    val expectedTypes = ExpectedTypesProvider.getExpectedTypes(call, true)
    if (expectedTypes.forall(!_.getType.isInstanceOf[PsiArrayType])) return false
    val resolve = ref.resolve()
    resolve match {
      case method: PsiMethod =>
        val clazz = method.getContainingClass
        if (clazz == null) return false
        val collectionClass = JavaPsiFacade.getInstance(call.getProject).findClass("java.util.Collection", GlobalSearchScope.allScope(call.getProject))
        if (!clazz.isInheritor(collectionClass, true)) return false
        true
      case _ => false
    }
  }
}
