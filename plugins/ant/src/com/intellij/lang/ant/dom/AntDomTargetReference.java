/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.lang.ant.AntSupport;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.refactoring.rename.BindablePsiReference;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ArrayListSet;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.StringTokenizer;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
* @author Eugene Zhuravlev
*/
class AntDomTargetReference extends AntDomReferenceBase implements BindablePsiReference{

  private final ReferenceGroup myGroup;

  public AntDomTargetReference(PsiElement element) {
    super(element, true);
    myGroup = null;
  }

  public AntDomTargetReference(PsiElement element, TextRange range, ReferenceGroup group) {
    super(element, range, true);
    myGroup = group;
    group.addReference(this);
  }

  public PsiElement resolve() {
    return ResolveCache.getInstance(getElement().getProject()).resolveWithCaching(this, MyResolver.INSTANCE, false, false);
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    final DomElement targetDomElement = toDomElement(element);
    if (targetDomElement != null) {
      final AntDomTarget pointingToTarget = targetDomElement.getParentOfType(AntDomTarget.class, false);
      if (pointingToTarget != null) {
          // the aim here is to receive all variants available at this particular context
          final TargetResolver.Result result = doResolve(null);
          if (result != null) {
            final Map<String, AntDomTarget> variants = result.getVariants();
            String newName = null;
            if (!variants.isEmpty()) {
              List<Couple<String>> prefixNamePairs = null;
              for (Map.Entry<String, AntDomTarget> entry : variants.entrySet()) {
                final AntDomTarget candidateTarget = entry.getValue();
                if (pointingToTarget.equals(candidateTarget)) {
                  final String candidateName = entry.getKey();
                  final String candidateTargetName = candidateTarget.getName().getRawText();
                  if (candidateName.endsWith(candidateTargetName)) {
                    final String prefix = candidateName.substring(0, candidateName.length() - candidateTargetName.length());
                    if (prefixNamePairs == null) {
                      prefixNamePairs = new ArrayList<>(); // lazy init
                    }
                    prefixNamePairs.add(Couple.of(prefix, candidateName));
                  }
                }
              }
              final String currentRefText = getCanonicalText();
              for (Couple<String> pair : prefixNamePairs) {
                final String prefix = pair.getFirst();
                final String effectiveName = pair.getSecond();
                if (currentRefText.startsWith(prefix)) {
                  if (newName == null || effectiveName.length() > newName.length()) {
                    // this candidate's prefix matches current reference text and this name is longer 
                    // than the previous candidate, then prefer this name
                    newName = effectiveName;
                  }
                }
              }
            }
            if (newName != null) {
              handleElementRename(newName);
              if (myGroup != null) {
                myGroup.textChanged(this, newName);
              }
            }
          }
      }
    }
    return getElement();
  }

  @Nullable 
  private AntDomElement getHostingAntDomElement() {
    final DomElement selfElement = DomUtil.getDomElement(getElement());
    if (selfElement == null) {
      return null;
    }
    return selfElement.getParentOfType(AntDomElement.class, false);
  }
  
  @NotNull
  public Object[] getVariants() {
    final TargetResolver.Result result = doResolve(getCanonicalText());
    if (result == null) {
      return EMPTY_ARRAY;
    }
    final Map<String, AntDomTarget> variants = result.getVariants();                                                                          
    final List resVariants = new ArrayList();
    final Set<String> existing = getExistingNames();
    for (String s : variants.keySet()) {
      if (existing.contains(s)){
        continue;
      }
      final LookupElementBuilder builder = LookupElementBuilder.create(s).withCaseSensitivity(false);
      final LookupElement element = AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE.applyPolicy(builder);
      resVariants.add(element);
    }
    return ContainerUtil.toArray(resVariants, new Object[resVariants.size()]);
  }

  @Nullable
  private TargetResolver.Result doResolve(@Nullable final String referenceText) {
    final AntDomElement hostingElement = getHostingAntDomElement();
    if (hostingElement == null) {
      return null;
    }
    AntDomProject projectToSearchFrom;
    AntDomTarget contextTarget;
    if (hostingElement instanceof AntDomAnt) {
      final PsiFileSystemItem antFile = ((AntDomAnt)hostingElement).getAntFilePath().getValue();
      projectToSearchFrom = antFile instanceof PsiFile ? AntSupport.getAntDomProjectForceAntFile((PsiFile)antFile) : null;
      contextTarget = null;
    }
    else {
      projectToSearchFrom = hostingElement.getContextAntProject();
      contextTarget = hostingElement.getParentOfType(AntDomTarget.class, false);
    }
    if (projectToSearchFrom == null) {
      return null;
    }
    return TargetResolver.resolve(projectToSearchFrom, contextTarget, referenceText == null ? Collections.emptyList() : Collections.singletonList(referenceText));
  }
  
  private Set<String> getExistingNames() {
    final AntDomElement hostingElement = getHostingAntDomElement();
    if (hostingElement == null) {
      return Collections.emptySet();
    }
    final AntDomTarget contextTarget = hostingElement.getParentOfType(AntDomTarget.class, false);
    if (contextTarget == null) {
      return Collections.emptySet();
    }
    final Set<String> existing = new ArrayListSet<>();
    final String selfName = contextTarget.getName().getStringValue();
    if (selfName != null) {
      existing.add(selfName);
    }
    final String dependsString = contextTarget.getDependsList().getRawText();
    if (dependsString != null) {
      final StringTokenizer tokenizer = new StringTokenizer(dependsString, ",", false);
      while (tokenizer.hasMoreTokens()) {
        existing.add(tokenizer.nextToken().trim());
      }
    }
    return existing;
  }

  public String getUnresolvedMessagePattern() {
    return AntBundle.message("cannot.resolve.target", getCanonicalText());
  }

  private static class MyResolver implements ResolveCache.Resolver {
    static final MyResolver INSTANCE = new MyResolver();
    
    public PsiElement resolve(@NotNull PsiReference psiReference, boolean incompleteCode) {
      final TargetResolver.Result result = ((AntDomTargetReference)psiReference).doResolve(psiReference.getCanonicalText());
      if (result == null) {
        return null;
      }
      final Pair<AntDomTarget,String> pair = result.getResolvedTarget(psiReference.getCanonicalText());
      final DomTarget domTarget = pair != null && pair.getFirst() != null ? DomTarget.getTarget(pair.getFirst()) : null;
      return domTarget != null? PomService.convertToPsi(domTarget) : null;
    }
  }
  
  public static class ReferenceGroup {
    private List<AntDomTargetReference> myRefs = new ArrayList<>();
    
    public void addReference(AntDomTargetReference ref) {
      myRefs.add(ref);
    }
    
    public void textChanged(AntDomTargetReference ref, String newText) {
      Integer lengthDelta = null;
      for (AntDomTargetReference r : myRefs) {
        if (lengthDelta != null) {
          r.setRangeInElement(r.getRangeInElement().shiftRight(lengthDelta));
        }
        else if (r.equals(ref)) {
          final TextRange range = r.getRangeInElement();
          final int oldLength = range.getLength();
          lengthDelta = new Integer(newText.length() - oldLength);
          r.setRangeInElement(range.grown(lengthDelta));
        }
      }
    }
  }
}
