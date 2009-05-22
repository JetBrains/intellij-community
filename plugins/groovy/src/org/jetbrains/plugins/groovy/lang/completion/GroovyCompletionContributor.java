package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.patterns.ElementPattern;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import com.intellij.psi.*;
import com.intellij.psi.filters.getters.InheritorsGetter;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.completion.handlers.AfterNewClassInsertHandler;
import org.jetbrains.plugins.groovy.lang.completion.handlers.ArrayInsertHandler;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ilyas
 */
public class GroovyCompletionContributor extends CompletionContributor {

  private static final ElementPattern<PsiElement> AFTER_NEW =
    psiElement().afterLeaf(psiElement().withText(PsiKeyword.NEW).andNot(psiElement().afterLeaf(psiElement().withText(PsiKeyword.THROW))))
//      .withLanguage(GroovyFileType.GROOVY_LANGUAGE);
       .withSuperParent(3, GrVariable.class);

  public GroovyCompletionContributor() {
    /*extend(CompletionType.SMART, psiElement(PsiElement.class), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        final PsiElement position = parameters.getPosition();
        final GrExpression expr = PsiTreeUtil.getContextOfType(position, GrExpression.class, true);
        ExpectedTypesProvider.getInstance(position.getProject()).getExpectedTypes(expr, true);
      }
    });*/
    extend(CompletionType.SMART, AFTER_NEW, new CompletionProvider<CompletionParameters>(false) {
      public void addCompletions(@NotNull final CompletionParameters parameters,
                                 final ProcessingContext matchingContext,
                                 @NotNull final CompletionResultSet result) {
        final PsiElement identifierCopy = parameters.getPosition();
        final PsiFile file = parameters.getOriginalFile();

        final List<PsiClassType> expectedClassTypes = new SmartList<PsiClassType>();
        final List<PsiArrayType> expectedArrayTypes = new ArrayList<PsiArrayType>();

        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            PsiType psiType = ((GrVariable)identifierCopy.getParent().getParent().getParent()).getTypeGroovy();
            //  for (PsiType psiType : getExpectedTypesInNewExpression((GrNewExpression)identifierCopy.getParent().getParent())) {
              if (psiType instanceof PsiClassType) {
                PsiType type = JavaCompletionUtil.eliminateWildcards(JavaCompletionUtil.originalize(psiType));
                final PsiClassType classType = (PsiClassType)type;
                if (classType.resolve() != null) {
                  expectedClassTypes.add(classType);
                }
              }
              else if (psiType instanceof PsiArrayType) {
                expectedArrayTypes.add((PsiArrayType)psiType);
              }
            }

//          }
        });

        for (final PsiArrayType type : expectedArrayTypes) {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              final LookupItem item = LookupItemUtil.objectToLookupItem(JavaCompletionUtil.eliminateWildcards(type));
              item.setAttribute(LookupItem.DONT_CHECK_FOR_INNERS, "");
              if (item.getObject() instanceof PsiClass) {
                JavaAwareCompletionData.setShowFQN(item);
              }
              item.setInsertHandler(new ArrayInsertHandler());
              result.addElement(item);
            }
          });
        }

        JavaSmartCompletionContributor.processInheritors(parameters, identifierCopy, file, expectedClassTypes, new Consumer<PsiType>() {
          public void consume(final PsiType type) {
            addExpectedType(result, type, identifierCopy);
          }
        }, result.getPrefixMatcher());
      }
    });
  }

  private static void addExpectedType(final CompletionResultSet result, final PsiType type, final PsiElement place) {
    if (!InheritorsGetter.hasAccessibleConstructor(type)) return;

    final PsiClass psiClass = PsiUtil.resolveClassInType(type);
    if (psiClass == null) return;

    if (psiClass.isInterface() || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) return;

    final LookupItem item = LookupItemUtil.objectToLookupItem(JavaCompletionUtil.eliminateWildcards(type));
    item.setAttribute(LookupItem.DONT_CHECK_FOR_INNERS, "");
    JavaAwareCompletionData.setShowFQN(item);
    item.setInsertHandler(new AfterNewClassInsertHandler((PsiClassType)type, place));
    result.addElement(item);
  }

  /*private static PsiType[] getExpectedTypesInNewExpression(GrNewExpression place) {
    final TypeConstraint[] constraints = GroovyExpectedTypesUtil.getExpectedTypes(place);
    PsiType[] res = new PsiType[constraints.length];
    for (int i = 0; i < constraints.length; i++) {
      res[i] = constraints[i].getType();
    }
    return res;*/
    /*final PsiElement parent = place.getParent();
    if (parent instanceof GrVariable) {
      return new PsiType[]{((GrVariable)place.getParent()).getTypeGroovy()};
    }
    if (parent instanceof GrArgumentList) {

      int argumentIndex = ((GrArgumentList)parent).getExpressionArgumentIndex(place);
      if (parent.getParent() instanceof GrCallExpression) {
        GrCallExpression call = (GrCallExpression)parent.getParent();
        final GroovyResolveResult[] variants = call.getMethodVariants();
        final List<PsiType> types = new ArrayList<PsiType>(variants.length);

        for (GroovyResolveResult variant : variants) {
          PsiElement element = variant.getElement();
          if (element instanceof PsiMethod) {
            PsiParameter[] parameters = ((PsiMethod)element).getParameterList().getParameters();
            if (parameters.length <= argumentIndex) continue;
            types.add(variant.getSubstitutor().substitute(parameters[argumentIndex].getType()));
          }
        }
        return types.toArray(new PsiType[types.size()]);
      }
  }

  return PsiType.EMPTY_ARRAY;
  }*/

}
