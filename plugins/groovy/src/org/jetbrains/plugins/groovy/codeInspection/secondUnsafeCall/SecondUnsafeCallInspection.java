/*
 * Copyright 2000-2007 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.codeInspection.secondUnsafeCall;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.annotator.inspections.SecondUnsafeCallQuickFix;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovySuppressableInspectionTool;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

/**
 * User: Dmitry.Krasilschikov
 * Date: 13.11.2007
 */
public class SecondUnsafeCallInspection extends GroovySuppressableInspectionTool {
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new GroovyPsiElementVisitor(new GroovyElementVisitor() {
      public void visitReferenceExpression(GrReferenceExpression refExpression) {
        checkForSecondUnsafeCall(refExpression, holder);
      }
    });
  }

  private void checkForSecondUnsafeCall(GrExpression expression, ProblemsHolder holder) {
    checkForSecondUnsafeCall(expression, holder, null);
  }

  private void checkForSecondUnsafeCall(GrExpression expression, ProblemsHolder holder, PsiElement hightlightElement) {
    if (hightlightElement == null) hightlightElement = expression;

    final GrReferenceExpression referenceExpression = (GrReferenceExpression) expression;

    if (GroovyElementTypes.mDOT.equals(referenceExpression.getDotTokenType())) {
      //        a?.b or a?.b()
      final GrExpression qualifierExpression = referenceExpression.getQualifierExpression();
      //        a?.b()
      if (qualifierExpression instanceof GrMethodCallExpression) {
        final GrExpression expression1 = ((GrMethodCallExpression) qualifierExpression).getInvokedExpression();
        //        a?.b
        if (!(expression1 instanceof GrReferenceExpression)) return;

        if (GroovyElementTypes.mOPTIONAL_DOT.equals(((GrReferenceExpression) expression1).getDotTokenType())) {
          holder.registerProblem(hightlightElement, GroovyInspectionBundle.message("call.can.throw.npe"), new SecondUnsafeCallQuickFix());
        }
      } else
        //        a?.b
        if (qualifierExpression instanceof GrReferenceExpression) {
          if (GroovyElementTypes.mOPTIONAL_DOT.equals(((GrReferenceExpression) qualifierExpression).getDotTokenType())) {
            holder.registerProblem(hightlightElement, GroovyInspectionBundle.message("call.can.throw.npe"), new SecondUnsafeCallQuickFix());
          }
        }
    }
  }

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return "Probable bugs";
  }

  @NotNull
  @Override
  public String[] getGroupPath() {
    return new String[]{"Groovy", getGroupDisplayName()};
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return GroovyInspectionBundle.message("second.unsafe.call");
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return "SecondUnsafeCall";
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  private IElementType getDotType(PsiElement element) {
    if (element instanceof GrReferenceExpression) return ((GrReferenceExpression) element).getDotTokenType();
    else if (element instanceof GrMethodCallExpression) {
      final GrExpression invokedExpr = ((GrMethodCallExpression) element).getInvokedExpression();

      if (invokedExpr instanceof GrReferenceExpression) return ((GrReferenceExpression) element).getDotTokenType();
    }

    return null;
  }
}


///*
// * Copyright 2000-2007 JetBrains s.r.o.
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package org.jetbrains.plugins.groovy.codeInspection.secondUnsafeCall;
//
//import org.jetbrains.annotations.Nls;
//import org.jetbrains.annotations.NonNls;
//import org.jetbrains.annotations.NotNull;
//import org.jetbrains.plugins.groovy.annotator.inspections.SecondUnsafeCallQuickFix;
//import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
//import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
//import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
//import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
//import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
//import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
//import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
//
///**
// * User: Dmitry.Krasilschikov
// * Date: 13.11.2007
// */
//public class SecondUnsafeCallInspection extends BaseInspection {
//  //@NotNull
//  //public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
//  //  return new GroovyPsiElementVisitor(new GroovyElementVisitor() {
//  //    public void visitReferenceExpression(GrReferenceExpression refExpression) {
//  //      checkForSecondUnsafeCall(refExpression, holder);
//  //    }
//  //  });
//  //}
//
//  //private void checkForSecondUnsafeCall(GrExpression expression, ProblemsHolder holder) {
//  //  checkForSecondUnsafeCall(expression, holder, null);
//  //}
//  //
//  //private void checkForSecondUnsafeCall(GrExpression expression, ProblemsHolder holder, PsiElement hightlightElement) {
//  //  if (hightlightElement == null) hightlightElement = expression;
//  //
//  //  final GrReferenceExpression referenceExpression = (GrReferenceExpression)expression;
//  //
//  //  if (GroovyElementTypes.mDOT.equals(referenceExpression.getDotTokenType())) {
//  //    //        a?.b or a?.b()
//  //    final GrExpression qualifierExpression = referenceExpression.getQualifierExpression();
//  //    //        a?.b()
//  //    if (qualifierExpression instanceof GrMethodCallExpression) {
//  //      final GrExpression expression1 = ((GrMethodCallExpression)qualifierExpression).getInvokedExpression();
//  //      //        a?.b
//  //      if (!(expression1 instanceof GrReferenceExpression)) return;
//  //
//  //      if (GroovyElementTypes.mOPTIONAL_DOT.equals(((GrReferenceExpression)expression1).getDotTokenType())) {
//  //        holder.registerProblem(hightlightElement, GroovyInspectionBundle.message("call.can.throw.npe"), new SecondUnsafeCallQuickFix());
//  //      }
//  //    }
//  //    else
//  //      //        a?.b
//  //      if (qualifierExpression instanceof GrReferenceExpression) {
//  //        if (GroovyElementTypes.mOPTIONAL_DOT.equals(((GrReferenceExpression)qualifierExpression).getDotTokenType())) {
//  //          holder.registerProblem(hightlightElement, GroovyInspectionBundle.message("call.can.throw.npe"), new SecondUnsafeCallQuickFix());
//  //        }
//  //      }
//  //  }
//  //}
//
//  @Nls
//  @NotNull
//  public String getGroupDisplayName() {
//    return GroovyInspectionBundle.message("second.unsafe.call");
//  }
//
//  @Nls
//  @NotNull
//  public String getDisplayName() {
//    return GroovyInspectionBundle.message("second.unsafe.call");
//  }
//
//  @NonNls
//  @NotNull
//  public String getShortName() {
//    return "SecondUnsafeCall";
//  }
//
//  protected BaseInspectionVisitor buildVisitor() {
//    return new BaseInspectionVisitor() {
//      public void visitReferenceExpression(GrReferenceExpression expression) {
//        //if (hightlightElement == null) hightlightElement = expression;
//
//        final GrReferenceExpression referenceExpression = (GrReferenceExpression)expression;
//
//        if (GroovyElementTypes.mDOT.equals(referenceExpression.getDotTokenType())) {
//          //        a?.b or a?.b()
//          final GrExpression qualifierExpression = referenceExpression.getQualifierExpression();
//          //        a?.b()
//          if (qualifierExpression instanceof GrMethodCallExpression) {
//            final GrExpression expression1 = ((GrMethodCallExpression)qualifierExpression).getInvokedExpression();
//            //        a?.b
//            if (!(expression1 instanceof GrReferenceExpression)) return;
//
//            if (GroovyElementTypes.mOPTIONAL_DOT.equals(((GrReferenceExpression)expression1).getDotTokenType())) {
//              registerError(expression, GroovyInspectionBundle.message("call.can.throw.npe")/*, new SecondUnsafeCallQuickFix()*/);
//            }
//          }
//          else
//            //        a?.b
//            if (qualifierExpression instanceof GrReferenceExpression) {
//              if (GroovyElementTypes.mOPTIONAL_DOT.equals(((GrReferenceExpression)qualifierExpression).getDotTokenType())) {
//                registerError(expression, GroovyInspectionBundle.message("call.can.throw.npe"),
//                                       new SecondUnsafeCallQuickFix());
//              }
//            }
//        }
//      }
//    };
//  }
//
//  //private static class Visitor extends BaseInspectionVisitor {
//  //
//  //  public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
//  //    super.visitReferenceExpression(referenceExpression);
//  //
//  //
//  //
//  //    final GrExpression returnValue = referenceExpression.getReturnValue();
//  //    if (returnValue != null) {
//  //      return;
//  //    }
//  //    final GrMethod method =
//  //        PsiTreeUtil.getParentOfType(referenceExpression, GrMethod.class);
//  //    if (method == null) {
//  //      return;
//  //    }
//  //    final GrOpenBlock body = method.getBlock();
//  //    if (body == null) {
//  //      return;
//  //    }
//  //    if (ControlFlowUtils.openBlockCompletesWithStatement(body, referenceExpression)) {
//  //      registerStatementError(referenceExpression);
//  //    }
//  //  }
//  //}
//
//  public boolean isEnabledByDefault() {
//    return true;
//  }
//
//  //private IElementType getDotType(PsiElement element) {
//  //  if (element instanceof GrReferenceExpression) {
//  //    return ((GrReferenceExpression)element).getDotTokenType();
//  //  }
//  //  else if (element instanceof GrMethodCallExpression) {
//  //    final GrExpression invokedExpr = ((GrMethodCallExpression)element).getInvokedExpression();
//  //
//  //    if (invokedExpr instanceof GrReferenceExpression) return ((GrReferenceExpression)element).getDotTokenType();
//  //  }
//  //
//  //  return null;
//  //}
//}
