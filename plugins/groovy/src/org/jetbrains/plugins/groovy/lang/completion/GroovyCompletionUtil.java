/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.AllClassesGetter;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClassTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.List;
import java.util.Set;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;
import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.*;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.*;

/**
 * @author ilyas
 */
public class GroovyCompletionUtil {

  private GroovyCompletionUtil() {
  }

  /**
   * Return true if last element of curren statement is expression
   *
   * @param statement
   * @return
   */
  public static boolean endsWithExpression(PsiElement statement) {
    while (statement != null &&
           !(statement instanceof GrExpression)) {
      statement = statement.getLastChild();
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

  /**
   * Shows wether keyword may be placed asas a new statement beginning
   *
   * @param element
   * @param canBeAfterBrace May be after '{' symbol or not
   * @return
   */
  public static boolean isNewStatement(PsiElement element, boolean canBeAfterBrace) {
    PsiElement previousLeaf = getLeafByOffset(element.getTextRange().getStartOffset() - 1, element);
    previousLeaf = PsiImplUtil.realPrevious(previousLeaf);
    if (previousLeaf != null && canBeAfterBrace && mLCURLY.equals(previousLeaf.getNode().getElementType())) {
      return true;
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
    if (element.getParent() instanceof GrTypeDefinitionBody) {
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

    final PsiElement parent1 = parent.getParent();
    if (!(parent1 instanceof GrVariableDeclaration)) return false;

    final GrVariableDeclaration variableDeclaration = (GrVariableDeclaration)parent1;
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
    if (context.getParent() instanceof GrReferenceExpression &&
        (context.getParent().getParent() instanceof GrOpenBlock ||
         context.getParent().getParent() instanceof GrClosableBlock) &&
        isNewStatement(context, true)) {
      return true;
    }

    if (context.getParent() instanceof GrReferenceExpression &&
        context.getParent().getParent() instanceof GrApplicationStatement &&
        (context.getParent().getParent().getParent() instanceof GrOpenBlock ||
         context.getParent().getParent().getParent() instanceof GrClosableBlock) &&
        isNewStatement(context, true)) {
      return true;
    }

    return context.getParent() instanceof GrTypeDefinitionBody &&
           isNewStatement(context, true);
  }

  public static boolean asTypedMethod(PsiElement context) {
    return context.getParent() instanceof GrReferenceElement &&
           context.getParent().getParent() instanceof GrTypeElement &&
           context.getParent().getParent().getParent() instanceof GrMethod &&
           context.getParent().getParent().getParent().getParent() instanceof GrTypeDefinitionBody &&
           context.getTextRange().getStartOffset() ==
           context.getParent().getParent().getParent().getParent().getTextRange().getStartOffset();

  }


  public static List<Object> getCompletionVariants(GroovyResolveResult[] candidates) {
    List<Object> result = CollectionFactory.arrayList();
    for (GroovyResolveResult candidate : candidates) {
      result.add(createCompletionVariant(candidate));
    }

    return result;
  }

  public static Object createCompletionVariant(GroovyResolveResult candidate) {
    final PsiElement element = candidate.getElement();
    final PsiElement context = candidate.getCurrentFileResolveContext();
    if (context instanceof GrImportStatement && element != null) {
      final String importedName = ((GrImportStatement)context).getImportedName();
      if (importedName != null) {
        final GrCodeReferenceElement importReference = ((GrImportStatement)context).getImportReference();
        if (importReference != null) {
          boolean alias = ((GrImportStatement)context).isAliasedImport();
          for (GroovyResolveResult r : importReference.multiResolve(false)) {
            final PsiElement resolved = r.getElement();
            if (context.getManager().areElementsEquivalent(resolved, element)) {
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
    else if (element instanceof PsiMethod) {
      final PsiMethod method;
      if (ResolveUtil.isInUseScope(candidate)) {
        method = generateMethodInCategory(candidate);
      }
      else {
        method = (PsiMethod)element;
      }
      return setupLookupBuilder(method, candidate.getSubstitutor(), LookupElementBuilder.create(candidate, ((PsiMethod)element).getName()));
    }
    if (element instanceof PsiClass) {
      return createClassLookupItem((PsiClass)element);
    }

    if (element instanceof PsiNamedElement) {
      return setupLookupBuilder(element, candidate.getSubstitutor(),
                                LookupElementBuilder.create(candidate, ((PsiNamedElement)element).getName()));
    }
    return candidate;
  }

  public static LookupElement createClassLookupItem(PsiClass psiClass) {
    assert psiClass.isValid();
    return AllClassesGetter.createLookupItem(psiClass, new GroovyClassNameInsertHandler());
  }

  private static LookupElement generateLookupForImportedElement(GroovyResolveResult resolveResult, String importedName, boolean alias) {
    final PsiElement element = resolveResult.getElement();
    assert element != null;
    if (!alias && element instanceof PsiClass) {
      return createClassLookupItem((PsiClass)element);
    }

    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    LookupElementBuilder builder = LookupElementBuilder.create(resolveResult, importedName).setPresentableText(importedName);
    return setupLookupBuilder(element, substitutor, builder);
  }

  private static PsiMethod generateMethodInCategory(GroovyResolveResult result) {
    final PsiElement element = result.getElement();
    assert element instanceof PsiMethod;
    final LightMethodBuilder builder = new LightMethodBuilder(element.getManager(), GroovyFileType.GROOVY_LANGUAGE, ((PsiMethod)element).getName());
    final PsiParameter[] params = ((PsiMethod)element).getParameterList().getParameters();
    for (int i = 1; i < params.length; i++) {
      builder.addParameter(params[i]);
    }
    builder.setBaseIcon(GroovyIcons.METHOD);
    return builder;
  }

  public static LookupElement getLookupElement(Object o) {
    if (o instanceof LookupElement) return (LookupElement)o;
    if (o instanceof PsiNamedElement) return generateLookupElement((PsiNamedElement)o);
    if (o instanceof PsiElement) return setupLookupBuilder((PsiElement)o, PsiSubstitutor.EMPTY, LookupElementBuilder.create(o, ((PsiElement)o).getText()));
    return LookupElementBuilder.create(o, o.toString());
  }
  public static LookupElementBuilder generateLookupElement(PsiNamedElement element) {
    LookupElementBuilder builder = LookupElementBuilder.create(element);
    return setupLookupBuilder(element, PsiSubstitutor.EMPTY, builder);
  }

  public static LookupElementBuilder setupLookupBuilder(PsiElement element, PsiSubstitutor substitutor, LookupElementBuilder builder) {
    builder = builder.setIcon(element.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS))
      .setInsertHandler(GroovyInsertHandler.INSTANCE);
    builder = setTailText(element, builder, substitutor);
    builder = setTypeText(element, builder, substitutor);
    return builder;
  }

  private static LookupElementBuilder setTailText(PsiElement element, LookupElementBuilder builder, PsiSubstitutor substitutor) {
    if (element instanceof PsiMethod) {
      builder = builder.setTailText(PsiFormatUtil.formatMethod((PsiMethod)element, substitutor, PsiFormatUtilBase.SHOW_PARAMETERS,
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
      builder = builder.setTailText(tailText, true);
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


  private static LookupElementBuilder setTypeText(PsiElement element, LookupElementBuilder builder, PsiSubstitutor substitutor) {
    if (element instanceof PsiVariable) {
      builder = builder.setTypeText(substitutor.substitute(((PsiVariable)element).getType()).getPresentableText());
    }
    else if (element instanceof PsiMethod) {
      final PsiType type = substitutor.substitute(((PsiMethod)element).getReturnType());
      if (type != null) {
        builder = builder.setTypeText(type.getPresentableText());
      }
    }
    return builder;
  }

  public static boolean hasConstructorParameters(PsiClass clazz) {
    final PsiMethod[] constructors;
    if (clazz instanceof GroovyPsiElement) {
      constructors = ResolveUtil.getAllClassConstructors(clazz, (GroovyPsiElement)clazz, PsiSubstitutor.EMPTY);
    }
    else {
      constructors = clazz.getConstructors();
    }

    for (PsiMethod constructor : constructors) {
      if (constructor.getParameterList().getParametersCount() > 0) return true;
    }

    return false;
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

    final Document document = FileDocumentManager.getInstance().getDocument(file.getViewProvider().getVirtualFile());

    int newStartOffset = startOffset;

    final PsiReference reference = file.findReferenceAt(startOffset);
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
    if (ref instanceof GrCodeReferenceElement && aClass.isValid()) {
      PsiElement newElement = ref.bindToElement(aClass);
      RangeMarker marker = document.createRangeMarker(newElement.getTextRange());
      CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(newElement);
      newStartOffset = marker.getStartOffset();
    }

    if (toDelete.isValid()) {
      document.deleteString(toDelete.getStartOffset(), toDelete.getEndOffset());
    }

    return newStartOffset;
  }

  //need to shorten references in type argument list
  public static void shortenReference(final PsiFile file, final int offset) throws IncorrectOperationException {
    final PsiDocumentManager manager = PsiDocumentManager.getInstance(file.getProject());
    final Document document = manager.getDocument(file);
    manager.commitDocument(document);
    final PsiReference ref = file.findReferenceAt(offset);
    if (ref instanceof GrCodeReferenceElement) {
      GrReferenceAdjuster.shortenReferences((GroovyPsiElement)ref);
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

  private static final Set<String> COLLECTION_METHOD_NAMES = CollectionFactory.newSet(
    "each", "eachWithIndex", "any", "every", "reverseEach", "collect", "collectAll", "find", "findAll", "retainAll", "removeAll", "split",
    "groupBy", "groupEntriesBy", "findLastIndexOf", "findIndexValues", "findIndexOf"
  );

  public static final Set<String> OPERATOR_METHOD_NAMES = CollectionFactory.newSet(
    "plus", "minus", "multiply", "power", "div", "mod", "or", "and", "xor", "next", "previous", "getAt", "putAt", "leftShift", "rightShift",
    "isCase", "bitwiseNegate", "negative", "positive"
  );
    
  public static boolean skipDefGroovyMethod(GrGdkMethod gdkMethod, PsiSubstitutor substitutor, @Nullable PsiType type) {
    if (type == null) return false;
    String name = gdkMethod.getStaticMethod().getName();

    final PsiType baseType = gdkMethod.getStaticMethod().getParameterList().getParameters()[0].getType();
    if (!TypeConversionUtil.erasure(baseType).equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) return false;

    final PsiType substituted = substitutor != null ? substitutor.substitute(baseType) : baseType;

    if (COLLECTION_METHOD_NAMES.contains(name)) {
      return !(type instanceof PsiArrayType ||
               InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_ITERABLE) ||
               substituted instanceof PsiArrayType ||
               InheritanceUtil.isInheritor(substituted, CommonClassNames.JAVA_LANG_ITERABLE));
    }
    if ("with".equals(name)) return false;

    return true;
  }

}
