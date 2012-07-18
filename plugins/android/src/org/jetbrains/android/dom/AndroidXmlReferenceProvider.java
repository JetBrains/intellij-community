package org.jetbrains.android.dom;

import com.intellij.codeInsight.completion.JavaLookupElementBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Processor;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidXmlReferenceProvider extends PsiReferenceProvider {
  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    if (!(element instanceof XmlTag)) {
      return PsiReference.EMPTY_ARRAY;
    }
    final Module module = ModuleUtilCore.findModuleForPsiElement(element);

    if (module == null || AndroidFacet.getInstance(module) == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final ASTNode startTagName = XmlChildRole.START_TAG_NAME_FINDER.findChild(element.getNode());

    final String baseClassQName = computeBaseClass((XmlTag)element);
    if (baseClassQName == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final List<PsiReference> result = new ArrayList<PsiReference>();
    final XmlTag tag = (XmlTag)element;

    if (startTagName != null && areReferencesProvidedByReferenceProvider(startTagName)) {
      addReferences(tag, startTagName.getPsi(), result, module, baseClassQName, true);
    }
    final ASTNode closingTagName = XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(element.getNode());

    if (closingTagName != null && areReferencesProvidedByReferenceProvider(closingTagName)) {
      addReferences(tag, closingTagName.getPsi(), result, module, baseClassQName, false);
    }
    return result.toArray(new PsiReference[result.size()]);
  }

  private static void addReferences(@NotNull XmlTag tag,
                                    @NotNull PsiElement nameElement,
                                    @NotNull List<PsiReference> result,
                                    @NotNull Module module,
                                    @NotNull String baseClassQName,
                                    boolean startTag) {
    final String text = nameElement.getText();
    if (text == null) {
      return;
    }
    final String[] nameParts = text.split("\\.");

    if (nameParts.length == 0) {
      return;
    }
    int offset = 0;

    for (int i = 0; i < nameParts.length; i++) {
      final String name = nameParts[i];

      if (name.length() > 0) {
        offset += name.length();
        final TextRange range = new TextRange(offset - name.length(), offset);
        final boolean isPackage = i < nameParts.length - 1;
        result.add(new MyClassOrPackageReference(tag, nameElement, range, isPackage, module, baseClassQName, startTag));
      }
      offset++;
    }
  }

  public static boolean areReferencesProvidedByReferenceProvider(ASTNode nameElement) {
    if (nameElement != null) {
      final PsiElement psiNameElement = nameElement.getPsi();
      final XmlTag tag = psiNameElement != null
                         ? PsiTreeUtil.getParentOfType(psiNameElement, XmlTag.class)
                         : null;
      if (tag != null) {
        final String baseClassQName = computeBaseClass(tag);

        if (baseClassQName != null) {
          final String text = nameElement.getText();
          return text != null && text.contains(".");
        }
      }
    }
    return false;
  }

  @Nullable
  private static String computeBaseClass(XmlTag context) {
    final XmlTag parentTag = context.getParentTag();
    final Pair<AndroidDomElement, String> pair =
      AndroidDomElementDescriptorProvider.getDomElementAndBaseClassQName(parentTag != null ? parentTag : context);
    return pair != null ? pair.getSecond() : null;
  }

  private static class MyClassOrPackageReference extends PsiReferenceBase<PsiElement> {
    private final PsiElement myNameElement;
    private final TextRange myRangeInNameElement;
    private final boolean myIsPackage;
    private final Module myModule;
    private final String myBaseClassQName;
    private final boolean myStartTag;

    public MyClassOrPackageReference(@NotNull XmlTag tag,
                                     @NotNull PsiElement nameElement,
                                     @NotNull TextRange rangeInNameElement,
                                     boolean isPackage,
                                     @NotNull Module module,
                                     @NotNull String baseClassQName,
                                     boolean startTag) {
      super(tag, rangeInParent(nameElement, rangeInNameElement), true);
      myNameElement = nameElement;
      myRangeInNameElement = rangeInNameElement;
      myIsPackage = isPackage;
      myModule = module;
      myBaseClassQName = baseClassQName;
      myStartTag = startTag;
    }

    private static TextRange rangeInParent(PsiElement element, TextRange range) {
      final int offset = element.getStartOffsetInParent();
      return new TextRange(range.getStartOffset() + offset, range.getEndOffset() + offset);
    }

    @Override
    public PsiElement resolve() {
      return ResolveCache.getInstance(myElement.getProject()).resolveWithCaching(this, new ResolveCache.Resolver() {
        @Nullable
        @Override
        public PsiElement resolve(@NotNull PsiReference reference, boolean incompleteCode) {
          return resolveInner();
        }
      }, false, false);
    }

    @Nullable
    private PsiElement resolveInner() {
      final int end = myRangeInNameElement.getEndOffset();
      final String value = myNameElement.getText().substring(0, end);
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(myElement.getProject());

      return myIsPackage ?
             facade.findPackage(value) :
             facade.findClass(value, myModule.getModuleWithDependenciesAndLibrariesScope(false));
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      final String prefix = myNameElement.getText().substring(0, myRangeInNameElement.getStartOffset());

      if (!myStartTag) {
        final ASTNode startTagNode = XmlChildRole.START_TAG_NAME_FINDER.findChild(myElement.getNode());
        if (startTagNode != null) {
          final String startTagName = startTagNode.getText();
          if (startTagName != null) {
             if (startTagName.startsWith(prefix)) {
               return new Object[]{startTagName.substring(prefix.length())};
             }
          }
        }
        return EMPTY_ARRAY;
      }
      final Project project = myModule.getProject();
      final PsiClass baseClass =
        JavaPsiFacade.getInstance(project).findClass(myBaseClassQName, myModule.getModuleWithDependenciesAndLibrariesScope(false));

      if (baseClass == null) {
        return EMPTY_ARRAY;
      }
      final List<Object> result = new ArrayList<Object>();

      ClassInheritorsSearch.search(baseClass, myModule.getModuleWithDependenciesAndLibrariesScope(false), true, true, false).forEach(
        new Processor<PsiClass>() {
          @Override
          public boolean process(PsiClass psiClass) {
            if (psiClass.getContainingClass() != null) {
              return true;
            }
            String name = psiClass.getQualifiedName();

            if (name != null && name.startsWith(prefix)) {
              name = name.substring(prefix.length());
              result.add(JavaLookupElementBuilder.forClass(psiClass, name, true));
            }
            return true;
          }
        });
      return ArrayUtil.toObjectArray(result);
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      final String newName = myIsPackage
                             ? ((PsiPackage)element).getQualifiedName()
                             : ((PsiClass)element).getQualifiedName();
      final ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(myNameElement);
      final TextRange range = new TextRange(0, myRangeInNameElement.getEndOffset());
      return manipulator != null ? manipulator.handleContentChange(myNameElement, range, newName) : element;
    }

    @Nullable
    @Override
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      final ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(myNameElement);
      assert manipulator != null : "Cannot find manipulator for " + myNameElement;
      return manipulator.handleContentChange(myNameElement, myRangeInNameElement, newElementName);
    }
  }
}
