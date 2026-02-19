// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.dom;

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ant.AntBundle;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class AntDomPropertyReference extends PsiPolyVariantReferenceBase<PsiElement> implements AntDomReference {

  public static final @NonNls String ANT_FILE_PREFIX = "ant.file.";
  public static final @NonNls String ANT_FILE_TYPE_PREFIX = "ant.file.type.";
  private final DomElement myInvocationContextElement;
  private boolean myShouldBeSkippedByAnnotator = false;

  public AntDomPropertyReference(DomElement invocationContextElement, XmlAttributeValue element, TextRange textRange) {
    super(element, textRange, true);
    myInvocationContextElement = invocationContextElement;
  }

  @Override
  public boolean shouldBeSkippedByAnnotator() {
    return myShouldBeSkippedByAnnotator;
  }

  @Override
  public String getUnresolvedMessagePattern() {
    return AntBundle.message("unknown.property", getCanonicalText());
  }


  @Override
  public void setShouldBeSkippedByAnnotator(boolean value) {
    myShouldBeSkippedByAnnotator = value;
  }

  @Override
  public @Nullable PsiElement resolve() {
    final ResolveResult res = doResolve();
    return res != null ? res.getElement() : null;
  }

  private @Nullable MyResolveResult doResolve() {
    final ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? (MyResolveResult)resolveResults[0] : null;
  }

  @Override
  public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    PsiElement element = getElement();
    PsiFile file = element.getContainingFile();
    return ResolveCache.getInstance(file.getProject()).resolveWithCaching(this, MyResolver.INSTANCE, false, incompleteCode,file);
  }

  @Override
  public Object @NotNull [] getVariants() {
    final AntDomProject project = myInvocationContextElement.getParentOfType(AntDomProject.class, true);
    if (project != null) {
      final Collection<String> variants = PropertyResolver.resolve(project.getContextAntProject(), getCanonicalText(), myInvocationContextElement).variants();
      Object[] result = new Object[variants.size()];
      int idx = 0;
      for (String variant : variants) {
        final LookupElementBuilder builder = LookupElementBuilder.create(variant).withCaseSensitivity(false);
        final LookupElement element = AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE.applyPolicy(builder);
        result[idx++] = element;
      }
      return result;
    }
    return EMPTY_ARRAY;
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    final MyResolveResult resolveResult = doResolve();
    if (resolveResult != null) {
      final PsiElement resolve = resolveResult.getElement();
      final PropertiesProvider provider = resolveResult.getProvider();
      final String refText = getCanonicalText();
      if (provider instanceof AntDomProject) {
        final DomElement resolvedDomElem = AntDomReferenceBase.toDomElement(resolve);
        if (provider.equals(resolvedDomElem)) {
          final String oldProjectName = ((AntDomProject)provider).getName().getValue();
          if (oldProjectName != null && refText.endsWith(oldProjectName)) {
            final String prefix = refText.substring(0, refText.length() - oldProjectName.length());
            newElementName = prefix + newElementName;
          }
        }
      }
      else if (provider instanceof AntDomProperty antProperty) {
        if (antProperty.equals(AntDomReferenceBase.toDomElement(resolve))) {
          String envPrefix = antProperty.getEnvironment().getValue();
          if (envPrefix != null) {
            if (!envPrefix.endsWith(".")) {
              envPrefix += ".";
            }
            if (refText.startsWith(envPrefix)) {
              final String envVariableName = refText.substring(envPrefix.length());
              final String newPrefix = newElementName.endsWith(".")? newElementName : newElementName + ".";
              newElementName = newPrefix + envVariableName;
            }
          }
        }
        else {
          final String prefix = antProperty.getPropertyPrefixValue();
          if (prefix != null) {
            newElementName = prefix + newElementName;
          }
        }
      }

    }
    return super.handleElementRename(newElementName);
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    // optimization to exclude obvious variants
    final DomElement domElement = AntDomReferenceBase.toDomElement(element);
    if (domElement instanceof AntDomProperty prop) {
      final String propName = prop.getName().getRawText();
      if (propName != null && prop.getPrefix().getRawText() == null && prop.getEnvironment().getRawText() == null) {
        // if only 'name' attrib is specified
        if (!propName.equalsIgnoreCase(getCanonicalText())) {
          return false;
        }
      }
    }
    return super.isReferenceTo(element);
  }

  private static class MyResolveResult implements ResolveResult {

    private final PsiElement myElement;
    private final PropertiesProvider myProvider;

    MyResolveResult(final PsiElement element, PropertiesProvider provider) {
      myElement = element;
      myProvider = provider;
    }

    @Override
    public PsiElement getElement() {
      return myElement;
    }

    public @Nullable PropertiesProvider getProvider() {
      return myProvider;
    }

    @Override
    public boolean isValidResult() {
      return true;
    }
  }

  private static class MyResolver implements ResolveCache.PolyVariantResolver<AntDomPropertyReference> {
    static final MyResolver INSTANCE = new MyResolver();

    @Override
    public ResolveResult @NotNull [] resolve(@NotNull AntDomPropertyReference antDomPropertyReference, boolean incompleteCode) {
      final List<ResolveResult> result = new ArrayList<>();
      final AntDomProject project = antDomPropertyReference.myInvocationContextElement.getParentOfType(AntDomProject.class, true);
      if (project != null) {
        final AntDomProject contextAntProject = project.getContextAntProject();
        final String propertyName = antDomPropertyReference.getCanonicalText();
        final PropertyResolver.@NotNull PropertyData resolved =
          PropertyResolver.resolve(contextAntProject, propertyName, antDomPropertyReference.myInvocationContextElement);
        final PsiElement mainDeclaration = resolved.element();

        if (mainDeclaration != null) {
          result.add(new MyResolveResult(mainDeclaration, resolved.provider()));
        }

        final List<PsiElement> antCallParams = AntCallParamsFinder.resolve(project, propertyName);
        for (PsiElement param : antCallParams) {
          result.add(new MyResolveResult(param, null));
        }
      }
      return result.toArray(ResolveResult.EMPTY_ARRAY);
    }
  }
}
