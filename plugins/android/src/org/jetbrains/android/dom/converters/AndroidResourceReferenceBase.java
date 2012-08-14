package org.jetbrains.android.dom.converters;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.dom.wrappers.FileResourceElementWrapper;
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper;
import org.jetbrains.android.dom.wrappers.ResourceElementWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.resourceManagers.ValueResourceInfo;
import org.jetbrains.android.resourceManagers.ValueResourceInfoImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidResourceReferenceBase extends PsiReferenceBase.Poly<XmlElement> {
  protected final AndroidFacet myFacet;
  protected final ResourceValue myResourceValue;

  public AndroidResourceReferenceBase(@NotNull GenericDomValue value,
                                      @Nullable TextRange range,
                                      @NotNull ResourceValue resourceValue,
                                      @NotNull AndroidFacet facet) {
    super(DomUtil.getValueElement(value), range, true);
    myResourceValue = resourceValue;
    myFacet = facet;
  }

  @Nullable
  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  @NotNull
  public PsiElement[] computeTargetElements() {
    final ResolveResult[] resolveResults = multiResolve(false);
    final List<PsiElement> results = new ArrayList<PsiElement>();

    for (ResolveResult result : resolveResults) {
      PsiElement element = result.getElement();

      if (element instanceof LazyValueResourceElementWrapper) {
        element = ((LazyValueResourceElementWrapper)element).computeElement();
      }

      if (element instanceof ResourceElementWrapper) {
        element = ((ResourceElementWrapper)element).getWrappee();
      }

      if (element != null) {
        results.add(element);
      }
    }
    return results.toArray(new PsiElement[results.size()]);
  }

  @NotNull
  @Override
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    return ResolveCache.getInstance(myElement.getProject())
      .resolveWithCaching(this, new ResolveCache.PolyVariantResolver<AndroidResourceReferenceBase>() {
        @NotNull
        @Override
        public ResolveResult[] resolve(@NotNull AndroidResourceReferenceBase reference, boolean incompleteCode) {
          return resolveInner();
        }
      }, false, incompleteCode);
  }

  @NotNull
  private ResolveResult[] resolveInner() {
    final List<PsiElement> elements = new ArrayList<PsiElement>();
    final List<PsiFile> files = new ArrayList<PsiFile>();
    collectTargets(myFacet, myResourceValue, elements, files);

    final List<ResolveResult> result = new ArrayList<ResolveResult>();

    for (PsiFile target : files) {
      if (target != null) {
        final PsiFile e = new FileResourceElementWrapper(target);
        result.add(new PsiElementResolveResult(e));
      }
    }

    for (PsiElement target : elements) {
      result.add(new PsiElementResolveResult(target));
    }
    return result.toArray(new ResolveResult[result.size()]);
  }

  private void collectTargets(AndroidFacet facet, ResourceValue resValue, List<PsiElement> elements, List<PsiFile> files) {
    String resType = resValue.getResourceType();
    if (resType == null) {
      return;
    }
    ResourceManager manager = facet.getResourceManager(resValue.getPackage());
    if (manager != null) {
      String resName = resValue.getResourceName();
      if (resName != null) {
        List<ValueResourceInfoImpl> valueResources = manager.findValueResourceInfos(resType, resName, false);

        for (final ValueResourceInfo resource : valueResources) {
          elements.add(new LazyValueResourceElementWrapper(resource, myElement));
        }
        if (resType.equals("id")) {
          elements.addAll(manager.findIdDeclarations(resName));
        }
        if (elements.size() == 0) {
          files.addAll(manager.findResourceFiles(resType, resName, false));
        }
      }
    }
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof LazyValueResourceElementWrapper) {
      element = ((LazyValueResourceElementWrapper)element).computeElement();

      if (element == null) {
        return false;
      }
    }

    final ResolveResult[] results = multiResolve(false);
    final PsiFile psiFile = element.getContainingFile();
    final VirtualFile vFile = psiFile != null ? psiFile.getVirtualFile() : null;

    for (ResolveResult result : results) {
      final PsiElement target = result.getElement();

      if (element.getManager().areElementsEquivalent(target, element)) {
        return true;
      }

      if (target instanceof LazyValueResourceElementWrapper && vFile != null) {
        final ValueResourceInfo info = ((LazyValueResourceElementWrapper)target).getResourceInfo();

        if (info.getContainingFile().equals(vFile)) {
          final XmlAttributeValue realTarget = info.computeXmlElement();

          if (element.getManager().areElementsEquivalent(realTarget, element)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return EMPTY_ARRAY;
  }
}
