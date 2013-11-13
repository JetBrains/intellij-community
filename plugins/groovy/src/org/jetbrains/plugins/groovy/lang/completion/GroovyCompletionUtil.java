/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
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

package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.AllClassesGetter;
import com.intellij.codeInsight.completion.JavaClassNameCompletionContributor;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.originInfo.OriginInfoProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.*;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.formatter.GeeseUtil;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClassTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrAnnotationCollector;
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.*;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;
import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.WHITE_SPACES_OR_COMMENTS;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.*;

/**
 * @author ilyas
 */
public class GroovyCompletionUtil {

  private GroovyCompletionUtil() {
  }

  /**
   * Return true if last element of current statement is expression
   *
   * @param statement
   * @return
   */
  public static boolean endsWithExpression(PsiElement statement) {
    while (statement != null &&
           !(statement instanceof GrExpression)) {
      statement = statement.getLastChild();
      if (statement instanceof PsiErrorElement) {
        statement = nearestLeftSibling(statement);
      }
    }
    return statement != null;
  }

  @Nullable
  public static PsiElement nearestLeftSibling(PsiElement elem) {
    elem = elem.getPrevSibling();
    while (elem != null &&
           (elem instanceof PsiWhiteSpace ||
            elem instanceof PsiComment ||
            mNLS.equals(elem.getNode().getElementType()))) {
      elem = elem.getPrevSibling();
    }
    return elem;
  }

  @Nullable
  public static PsiElement nearestLeftLeaf(PsiElement elem) {
    elem = PsiTreeUtil.prevLeaf(elem);
    while (elem != null &&
           (elem instanceof PsiWhiteSpace ||
            elem instanceof PsiComment ||
            mNLS.equals(elem.getNode().getElementType()))) {
      elem = PsiTreeUtil.prevLeaf(elem);
    }
    return elem;
  }

  /**
   * Shows whether keyword may be placed as a new statement beginning
   *
   * @param element
   * @param canBeAfterBrace May be after '{' symbol or not
   * @return
   */
  public static boolean isNewStatement(PsiElement element, boolean canBeAfterBrace) {
    PsiElement previousLeaf = getLeafByOffset(element.getTextRange().getStartOffset() - 1, element);
    previousLeaf = PsiImplUtil.realPrevious(previousLeaf);
    if (previousLeaf != null) {
      if (canBeAfterBrace && mLCURLY.equals(previousLeaf.getNode().getElementType())) {
        return true;
      }
      if (mCOLON.equals(previousLeaf.getNode().getElementType()) && previousLeaf.getParent() instanceof GrLabeledStatement) {
        return true;
      }
    }
    return (previousLeaf == null || SEPARATORS.contains(previousLeaf.getNode().getElementType()));
  }

  @Nullable
  public static PsiElement getLeafByOffset(int offset, PsiElement element) {
    if (offset < 0) {
      return null;
    }
    PsiElement candidate = element.getContainingFile();
    while (candidate.getNode().getFirstChildNode() != null) {
      candidate = candidate.findElementAt(offset);
    }
    return candidate;
  }

  /**
   * return true, if the element is first element after modifiers and there is no type element
   */
  public static boolean isFirstElementAfterPossibleModifiersInVariableDeclaration(PsiElement element, boolean acceptParameter) {
    if (element.getParent() instanceof GrTypeDefinitionBody && !(element instanceof PsiComment)) {
      //is first on the line?
      String text = element.getContainingFile().getText();
      int i = CharArrayUtil.shiftBackward(text, element.getTextRange().getStartOffset() - 1, " \t");
      return i >= 0 && (text.charAt(i) == '\n' || text.charAt(i) == '{');
    }

    final PsiElement parent = element.getParent();
    if (!(parent instanceof GrVariable)) return false;

    if (acceptParameter && parent instanceof GrParameter) {
      return ((GrParameter)parent).getTypeElementGroovy() == null;
    }

    final PsiElement pparent = parent.getParent();
    if (!(pparent instanceof GrVariableDeclaration)) return false;
    if (((GrVariableDeclaration)pparent).isTuple()) return false;

    final GrVariableDeclaration variableDeclaration = (GrVariableDeclaration)pparent;
    if (variableDeclaration.getTypeElementGroovy() != null) return false;

    return variableDeclaration.getVariables()[0] == parent;
  }

  private static final TokenSet SEPARATORS = TokenSet.create(mNLS,
                                                             mSEMI);

  public static boolean asSimpleVariable(PsiElement context) {
    return isInTypeDefinitionBody(context) &&
           isNewStatement(context, true);
  }

  public static boolean isInTypeDefinitionBody(PsiElement context) {
    return (context.getParent() instanceof GrCodeReferenceElement &&
            context.getParent().getParent() instanceof GrClassTypeElement &&
            context.getParent().getParent().getParent() instanceof GrTypeDefinitionBody) ||
           context.getParent() instanceof GrTypeDefinitionBody;
  }

  public static boolean asVariableInBlock(PsiElement context) {
    if (context.getParent() instanceof GrReferenceExpression) {
      PsiElement parent = context.getParent().getParent();
      while (parent instanceof GrStatement) {
        parent = parent.getParent();
      }
      if ((parent instanceof GrControlFlowOwner || parent instanceof GrCaseSection) && isNewStatement(context, true)) {
        return true;
      }
    }

    return context.getParent() instanceof GrTypeDefinitionBody && isNewStatement(context, true);
  }

  public static boolean asTypedMethod(PsiElement context) {
    return context.getParent() instanceof GrReferenceElement &&
           context.getParent().getParent() instanceof GrTypeElement &&
           context.getParent().getParent().getParent() instanceof GrMethod &&
           context.getParent().getParent().getParent().getParent() instanceof GrTypeDefinitionBody &&
           context.getTextRange().getStartOffset() ==
           context.getParent().getParent().getParent().getParent().getTextRange().getStartOffset();
  }


  public static List<LookupElement> getCompletionVariants(GroovyResolveResult[] candidates,
                                                          boolean afterNew,
                                                          PrefixMatcher matcher,
                                                          PsiElement position) {
    List<LookupElement> result = ContainerUtil.newArrayList();
    for (GroovyResolveResult candidate : candidates) {
      result.addAll(createLookupElements(candidate, afterNew, matcher, position));
      ProgressManager.checkCanceled();
    }

    return result;
  }

  public static List<LookupElement> getCompletionVariants(List<GroovyResolveResult> candidates,
                                                          boolean afterNew,
                                                          PrefixMatcher matcher,
                                                          PsiElement position) {
    List<LookupElement> result = ContainerUtil.newArrayList();
    for (GroovyResolveResult candidate : candidates) {
      result.addAll(createLookupElements(candidate, afterNew, matcher, position));
      ProgressManager.checkCanceled();
    }

    return result;
  }


  public static List<? extends LookupElement> createLookupElements(GroovyResolveResult candidate,
                                                                   boolean afterNew,
                                                                   PrefixMatcher matcher,
                                                                   @Nullable PsiElement position) {
    final PsiElement element = candidate.getElement();
    final PsiElement context = candidate.getCurrentFileResolveContext();
    if (context instanceof GrImportStatement && element != null) {
      if (element instanceof PsiPackage) {
        return Collections.emptyList();
      }

      final String importedName = ((GrImportStatement)context).getImportedName();
      if (importedName != null) {
        if (!(matcher.prefixMatches(importedName) ||
              element instanceof PsiMethod && getterMatches(matcher, (PsiMethod)element, importedName) ||
              element instanceof PsiMethod && setterMatches(matcher, (PsiMethod)element, importedName))
          ) {
          return Collections.emptyList();
        }

        final GrCodeReferenceElement importReference = ((GrImportStatement)context).getImportReference();
        if (importReference != null) {
          boolean alias = ((GrImportStatement)context).isAliasedImport();
          for (GroovyResolveResult r : importReference.multiResolve(false)) {
            final PsiElement resolved = r.getElement();
            if (context.getManager().areElementsEquivalent(resolved, element) && (alias || !(element instanceof PsiClass))) {
              return generateLookupForImportedElement(candidate, importedName, alias);
            }
            else {
              if (resolved instanceof PsiField && element instanceof PsiMethod && isAccessorFor((PsiMethod)element, (PsiField)resolved)) {
                return generateLookupForImportedElement(candidate, getAccessorPrefix((PsiMethod)element) + capitalize(importedName), alias);
              }
            }
          }
        }
      }
    }

    String name = element instanceof PsiNamedElement ? ((PsiNamedElement)element).getName() :   element.getText();
    if (name == null || !matcher.prefixMatches(name)) {
      return Collections.emptyList();
    }

    if (element instanceof PsiClass) {
      return JavaClassNameCompletionContributor
        .createClassLookupItems((PsiClass)element, afterNew, new GroovyClassNameInsertHandler(), Condition.TRUE);
    }

    LookupElementBuilder builder = LookupElementBuilder.create(element instanceof PsiPackage ? element : candidate, name);
    return Arrays.asList(setupLookupBuilder(element, candidate.getSubstitutor(), builder, position));
  }

  private static boolean setterMatches(PrefixMatcher matcher, PsiMethod element, String importedName) {
    return isSimplePropertySetter(element) && matcher.prefixMatches(getSetterName(importedName));
  }

  private static boolean getterMatches(PrefixMatcher matcher, PsiMethod element, String importedName) {
    return isSimplePropertyGetter(element) &&
           (matcher.prefixMatches(getGetterNameNonBoolean(importedName)) ||
            element.getReturnType() == PsiType.BOOLEAN && matcher.prefixMatches(getGetterNameBoolean(importedName)));
  }

  public static LookupElement createClassLookupItem(PsiClass psiClass) {
    assert psiClass.isValid();
    return AllClassesGetter.createLookupItem(psiClass, new GroovyClassNameInsertHandler());
  }

  private static List<? extends LookupElement> generateLookupForImportedElement(GroovyResolveResult resolveResult,
                                                                                String importedName,
                                                                                boolean alias) {
    final PsiElement element = resolveResult.getElement();
    assert element != null;
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    LookupElementBuilder builder = LookupElementBuilder.create(resolveResult, importedName).withPresentableText(importedName);
    return Arrays.asList(setupLookupBuilder(element, substitutor, builder, null));
  }

  public static LookupElement createLookupElement(PsiNamedElement o) {
    return setupLookupBuilder(o, PsiSubstitutor.EMPTY, LookupElementBuilder.create(o, o.getName()), null);
  }

  private static LookupElementBuilder setupLookupBuilder(PsiElement element,
                                                         PsiSubstitutor substitutor,
                                                         LookupElementBuilder builder,
                                                         @Nullable PsiElement position) {
    builder = builder.withIcon(element.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS))
      .withInsertHandler(GroovyInsertHandler.INSTANCE);
    builder = setTailText(element, builder, substitutor);
    builder = setTypeText(element, builder, substitutor, position);
    return builder;
  }

  private static LookupElementBuilder setTailText(PsiElement element, LookupElementBuilder builder, PsiSubstitutor substitutor) {
    if (element instanceof PsiMethod) {
      PsiClass aClass = ((PsiMethod)element).getContainingClass();
      if (aClass != null && aClass.isAnnotationType()) {
        return builder;
      }
      builder = builder.withTailText(PsiFormatUtil.formatMethod((PsiMethod)element, substitutor, PsiFormatUtilBase.SHOW_PARAMETERS,
                                                                PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE));
    }
    else if (element instanceof PsiClass) {
      String tailText = getPackageText((PsiClass)element);
      final PsiClass psiClass = (PsiClass)element;
      if ((substitutor == null || substitutor.getSubstitutionMap().size() == 0) && psiClass.getTypeParameters().length > 0) {
        tailText = "<" + StringUtil.join(psiClass.getTypeParameters(), new Function<PsiTypeParameter, String>() {
          public String fun(PsiTypeParameter psiTypeParameter) {
            return psiTypeParameter.getName();
          }
        }, "," + (showSpaceAfterComma(psiClass) ? " " : "")) + ">" + tailText;
      }
      builder = builder.withTailText(tailText, true);
    }

    String originInfo = OriginInfoProvider.getOriginInfo(element);
    if (originInfo != null) {
      builder = builder.appendTailText(" " + originInfo, true);
    }

    return builder;
  }

  private static String getPackageText(PsiClass psiClass) {
    @NonNls String packageName = PsiFormatUtil.getPackageDisplayName(psiClass);
    return " (" + packageName + ")";
  }


  private static boolean showSpaceAfterComma(PsiClass element) {
    return CodeStyleSettingsManager.getSettings(element.getProject()).SPACE_AFTER_COMMA;
  }


  private static LookupElementBuilder setTypeText(PsiElement element,
                                                  LookupElementBuilder builder,
                                                  PsiSubstitutor substitutor,
                                                  @Nullable PsiElement position) {
    PsiType type = null;
    if (element instanceof GrVariable) {
      if (position != null && GroovyRefactoringUtil.isLocalVariable(element)) {
        type = TypeInferenceHelper.getInferredType(position, ((GrVariable)element).getName());
      }
      else {
        type = ((GrVariable)element).getTypeGroovy();
      }
    }
    else if (element instanceof PsiVariable) {
      type = ((PsiVariable)element).getType();
    }
    else if (element instanceof PsiMethod) {
      type = substitutor.substitute(((PsiMethod)element).getReturnType());
    }
    return type != null ? builder.withTypeText(type.getPresentableText()) : builder;
  }

  public static boolean hasConstructorParameters(@NotNull PsiClass clazz, @NotNull PsiElement place) {
    final GroovyResolveResult[] constructors = ResolveUtil.getAllClassConstructors(clazz, PsiSubstitutor.EMPTY, null, place);


    boolean hasSetters = ContainerUtil.find(clazz.getAllMethods(), new Condition<PsiMethod>() {
      @Override
      public boolean value(PsiMethod method) {
        return isSimplePropertySetter(method);
      }
    }) != null;

    boolean hasParameters = false;
    boolean hasAccessibleConstructors = false;
    for (GroovyResolveResult result : constructors) {
      final PsiElement element = result.getElement();
      if (element instanceof PsiMethod) {
        if (((PsiMethod)element).getParameterList().getParametersCount() > 0) {
          hasParameters = true;
        }
        if (result.isAccessible()) {
          hasAccessibleConstructors = true;
        }
        if (hasAccessibleConstructors && hasParameters) return true;
      }
    }

    return !hasAccessibleConstructors && (hasParameters || hasSetters);
  }

  public static void addImportForItem(PsiFile file, int startOffset, LookupItem item) throws IncorrectOperationException {
    PsiDocumentManager.getInstance(file.getProject()).commitAllDocuments();

    Object o = item.getObject();
    if (o instanceof PsiClass) {
      PsiClass aClass = (PsiClass)o;
      if (aClass.getQualifiedName() == null) return;
      final String lookupString = item.getLookupString();
      int length = lookupString.length();
      final int i = lookupString.indexOf('<');
      if (i >= 0) length = i;
      final int newOffset = addImportForClass(file, startOffset, startOffset + length, aClass);
      shortenReference(file, newOffset);
    }
    else if (o instanceof PsiType) {
      PsiType type = ((PsiType)o).getDeepComponentType();
      if (type instanceof PsiClassType) {
        PsiClass refClass = ((PsiClassType)type).resolve();
        if (refClass != null) {
          int length = refClass.getName().length();
          addImportForClass(file, startOffset, startOffset + length, refClass);
        }
      }
    }
  }

  public static int addImportForClass(PsiFile file, int startOffset, int endOffset, PsiClass aClass) throws IncorrectOperationException {
//    LOG.assertTrue(CommandProcessor.getInstance().getCurrentCommand() != null);
//    LOG.assertTrue(
//      ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().getCurrentWriteAction(null) != null);

    final PsiManager manager = file.getManager();

    final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);

    int newStartOffset = startOffset;

    final PsiReference reference = file.findReferenceAt(endOffset - 1);
    if (reference != null) {
      final PsiElement resolved = reference.resolve();
      if (resolved instanceof PsiClass) {
        if (((PsiClass)resolved).getQualifiedName() == null || manager.areElementsEquivalent(aClass, resolved)) {
          return newStartOffset;
        }
      }
    }

    String name = aClass.getName();
    document.replaceString(startOffset, endOffset, name);

    final RangeMarker toDelete = JavaCompletionUtil.insertTemporary(endOffset, document, " ");

    PsiDocumentManager.getInstance(manager.getProject()).commitAllDocuments();

    final PsiReference ref = file.findReferenceAt(startOffset);
    if (ref instanceof GrReferenceElement && aClass.isValid()) {
      PsiElement newElement = ref.bindToElement(aClass);
      RangeMarker marker = document.createRangeMarker(newElement.getTextRange());
      CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(newElement);
      newStartOffset = marker.getStartOffset();
    }

    if (toDelete.isValid()) {
      document.deleteString(toDelete.getStartOffset(), toDelete.getEndOffset());
    }

    return newStartOffset;
  }

  //need to shorten references in type argument list
  public static void shortenReference(final PsiFile file, final int offset) throws IncorrectOperationException {
    final Project project = file.getProject();
    final PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    final Document document = manager.getDocument(file);
    assert document != null;
    manager.commitDocument(document);
    final PsiReference ref = file.findReferenceAt(offset);
    if (ref instanceof GrCodeReferenceElement) {
      JavaCodeStyleManager.getInstance(project).shortenClassReferences((GroovyPsiElement)ref);
    }
  }

  public static int addRParenth(Editor editor, int oldTail, boolean space_within_cast_parentheses) {
    int offset = -1;

    final HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(oldTail);
    while (!iterator.atEnd()) {
      final IElementType tokenType = iterator.getTokenType();
      if (WHITE_SPACES_OR_COMMENTS.contains(tokenType)) {
        iterator.advance();
        continue;
      }
      if (tokenType == mRPAREN) {
        offset = iterator.getEnd();
      }
      break;
    }
    if (offset != -1) return offset;
    offset = oldTail;
    if (space_within_cast_parentheses) {
      offset = TailType.insertChar(editor, oldTail, ' ');
    }
    return TailType.insertChar(editor, offset, ')');
  }

  public static final Set<String> OPERATOR_METHOD_NAMES = ContainerUtil.newHashSet(
    "plus", "minus", "multiply", "power", "div", "mod", "or", "and", "xor", "next", "previous", "getAt", "putAt", "leftShift", "rightShift",
    "isCase", "bitwiseNegate", "negative", "positive", "call"
  );

  public static boolean skipDefGroovyMethod(GrGdkMethod gdkMethod, PsiSubstitutor substitutor, @Nullable PsiType type) {
    if (type == null) return false;
    String name = gdkMethod.getStaticMethod().getName();

    final PsiType baseType = gdkMethod.getStaticMethod().getParameterList().getParameters()[0].getType();
    if (!TypeConversionUtil.erasure(baseType).equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) return false;

    final PsiType substituted = substitutor != null ? substitutor.substitute(baseType) : baseType;

    if (GdkMethodUtil.COLLECTION_METHOD_NAMES.contains(name)) {
      return !(type instanceof PsiArrayType ||
               InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_ITERABLE) ||
               substituted instanceof PsiArrayType ||
               InheritanceUtil.isInheritor(substituted, CommonClassNames.JAVA_LANG_ITERABLE));
    }
    if (GdkMethodUtil.isWithName(name)) return false;

    return true;
  }

  /*
  we are here:  foo(List<? <caret> ...
   */
  public static boolean isWildcardCompletion(PsiElement position) {
    PsiElement prev = GeeseUtil.getPreviousNonWhitespaceToken(position);
    if (prev instanceof PsiErrorElement) prev = GeeseUtil.getPreviousNonWhitespaceToken(prev);

    if (prev == null || prev.getNode().getElementType() != mQUESTION) return false;

    final PsiElement pprev = GeeseUtil.getPreviousNonWhitespaceToken(prev);
    if (pprev == null) return false;

    final IElementType t = pprev.getNode().getElementType();
    return t == mLT || t == mCOMMA;
  }

  public static List<LookupElement> getAnnotationCompletionResults(GrAnnotation anno, PrefixMatcher matcher) {
    if (anno != null) {
      GrCodeReferenceElement ref = anno.getClassReference();
      PsiElement resolved = ref.resolve();

      if (resolved instanceof PsiClass) {
        final PsiAnnotation annotationCollector = GrAnnotationCollector.findAnnotationCollector((PsiClass)resolved);

        if (annotationCollector != null) {
          final ArrayList<GrAnnotation> annotations = ContainerUtil.newArrayList();
          GrAnnotationCollector.collectAnnotations(annotations, anno, annotationCollector);

          Set<String> usedNames = ContainerUtil.newHashSet();
          List<LookupElement> result = new ArrayList<LookupElement>();
          for (GrAnnotation annotation : annotations) {
            final PsiElement resolvedAliased = annotation.getClassReference().resolve();
            if (resolvedAliased instanceof PsiClass && ((PsiClass)resolvedAliased).isAnnotationType()) {
              for (PsiMethod method : ((PsiClass)resolvedAliased).getMethods()) {
                if (usedNames.add(method.getName())) {
                  result.addAll(createLookupElements(new GroovyResolveResultImpl(method, true), false, matcher, null));
                }
              }
            }
          }
          return result;
        }
        else if (((PsiClass)resolved).isAnnotationType()) {
          List<LookupElement> result = new ArrayList<LookupElement>();
          for (PsiMethod method : ((PsiClass)resolved).getMethods()) {
            result.addAll(createLookupElements(new GroovyResolveResultImpl(method, true), false, matcher, null));
          }
          return result;
        }
      }
    }

    return Collections.emptyList();
  }

  static boolean isNewStatementInScript(PsiElement context) {
    final PsiElement leaf = getLeafByOffset(context.getTextRange().getStartOffset() - 1, context);
    if (leaf != null && isNewStatement(context, false)) {
      PsiElement parent = leaf.getParent();
      if (parent instanceof GroovyFile) {
        return true;
      }
    }
    return false;
  }

  public static boolean isReferenceElementInNewExpr(PsiElement context) {
    if (context.getParent() instanceof GrCodeReferenceElement) {
      PsiElement pparent = context.getParent().getParent();
      if (pparent instanceof GrNewExpression) return true;
    }

    return false;
  }

  static boolean isCodeReferenceElementApplicableToModifierCompletion(PsiElement context) {
    return context.getParent() instanceof GrCodeReferenceElement &&
           !(context.getParent().getParent() instanceof GrImportStatement) &&
           !(context.getParent().getParent() instanceof GrPackageDefinition) &&
           !(context.getParent().getParent() instanceof GrNewExpression);
  }

  static boolean isTypelessParameter(PsiElement context) {
    return (context.getParent() instanceof GrParameter && ((GrParameter)context.getParent()).getTypeElementGroovy() == null);
  }

  public static boolean isTupleVarNameWithoutTypeDeclared(PsiElement position) {
    PsiElement parent = position.getParent();
    PsiElement pparent = parent.getParent();
    return parent instanceof GrVariable &&
           ((GrVariable)parent).getNameIdentifierGroovy() == position &&
           ((GrVariable)parent).getTypeElementGroovy() == null &&
           pparent instanceof GrVariableDeclaration &&
           ((GrVariableDeclaration)pparent).isTuple();
  }
}
