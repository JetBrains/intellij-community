/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.ant.dom;

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ant.AntBundle;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 21, 2010
 */
public class AntDomPropertyReference extends PsiPolyVariantReferenceBase<PsiElement> implements AntDomReference {

  public static final String ANT_FILE_PREFIX = "ant.file.";
  private final DomElement myInvocationContextElement;
  private boolean myShouldBeSkippedByAnnotator = false;
  
  public AntDomPropertyReference(DomElement invocationContextElement, XmlAttributeValue element, TextRange textRange) {
    super(element, textRange, true);
    myInvocationContextElement = invocationContextElement;
  }

  public boolean shouldBeSkippedByAnnotator() {
    return myShouldBeSkippedByAnnotator;
  }

  public String getUnresolvedMessagePattern() {
    return AntBundle.message("unknown.property", getCanonicalText());
  }


  public void setShouldBeSkippedByAnnotator(boolean value) {
    myShouldBeSkippedByAnnotator = value;
  }

  @Nullable
  public PsiElement resolve() {
    final ResolveResult res = doResolve();
    return res != null ? res.getElement() : null;
  }

  @Nullable
  private MyResolveResult doResolve() {
    final ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? (MyResolveResult)resolveResults[0] : null;
  }
  
  @NotNull 
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    return ((PsiManagerEx)getElement().getManager()).getResolveCache().resolveWithCaching(this, MyResolver.INSTANCE, false, incompleteCode);
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    final AntDomProject project = myInvocationContextElement.getParentOfType(AntDomProject.class, true);
    if (project != null) {
      final Collection<String> variants = PropertyResolver.resolve(project.getContextAntProject(), getCanonicalText(), myInvocationContextElement).getSecond();
      Object[] result = new Object[variants.size()];
      int idx = 0;
      for (String variant : variants) {
        final LookupElementBuilder builder = LookupElementBuilder.create(variant).setCaseSensitive(false);
        final LookupElement element = AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE.applyPolicy(builder);
        result[idx++] = element;
      }
      return result;
    }
    return EMPTY_ARRAY;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final MyResolveResult resolveResult = doResolve();
    if (resolveResult != null) {
      final PsiElement resolve = resolveResult.getElement();
      final String refText = getCanonicalText();
      if (refText.startsWith(ANT_FILE_PREFIX)) {
        if (resolve instanceof PomTargetPsiElement) {
          final PomTarget target = ((PomTargetPsiElement)resolve).getTarget();
          if(target instanceof DomTarget) {
            final DomTarget domTarget = (DomTarget)target;
            final String oldName = domTarget.getName();
            if (oldName != null && (refText.length() == ANT_FILE_PREFIX.length() + oldName.length()) && refText.endsWith(oldName)) {
              newElementName = ANT_FILE_PREFIX + newElementName;
            }
          }
        }
      }
      else {
        final PropertiesProvider provider = resolveResult.getProvider();
        final String prefix = provider instanceof AntDomProperty? ((AntDomProperty)provider).getPropertyPrefixValue() : null;
        if (prefix != null) {
          newElementName = prefix + newElementName;
        }
      }
    }
    return super.handleElementRename(newElementName);
  }

  private static class MyResolveResult implements ResolveResult {

    private final PsiElement myElement;
    private final PropertiesProvider myProvider;

    public MyResolveResult(final PsiElement element, PropertiesProvider provider) {
      myElement = element;
      myProvider = provider;
    }

    public PsiElement getElement() {
      return myElement;
    }

    @Nullable
    public PropertiesProvider getProvider() {
      return myProvider;
    }

    public boolean isValidResult() {
      return true;
    }
  }

  private static class MyResolver implements ResolveCache.PolyVariantResolver<AntDomPropertyReference> {
    static final MyResolver INSTANCE = new MyResolver();
    
    public ResolveResult[] resolve(AntDomPropertyReference antDomPropertyReference, boolean incompleteCode) {
      final List<ResolveResult> result = new ArrayList<ResolveResult>();
      final AntDomProject project = antDomPropertyReference.myInvocationContextElement.getParentOfType(AntDomProject.class, true);
      if (project != null) {
        final AntDomProject contextAntProject = project.getContextAntProject();
        final String propertyName = antDomPropertyReference.getCanonicalText();
        final Trinity<PsiElement,Collection<String>,PropertiesProvider> resolved = 
          PropertyResolver.resolve(contextAntProject, propertyName, antDomPropertyReference.myInvocationContextElement);
        final PsiElement mainDeclaration = resolved.getFirst();
    
        if (mainDeclaration != null) {
          result.add(new MyResolveResult(mainDeclaration, resolved.getThird()));
        }

        final List<PsiElement> antCallParams = AntCallParamsFinder.resolve(project, propertyName);
        for (PsiElement param : antCallParams) {
          result.add(new MyResolveResult(param, null));
        }
      }
      return ContainerUtil.toArray(result, new ResolveResult[result.size()]);
    }
  }
}
