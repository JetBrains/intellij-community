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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ArrayListSet;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.StringTokenizer;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
* @author Eugene Zhuravlev
*         Date: Aug 17, 2010
*/
class AntDomTargetReference extends AntDomReferenceBase {

  private final GenericDomValue<TargetResolver.Result> myValue;

  public AntDomTargetReference(PsiElement element, GenericDomValue<TargetResolver.Result> value) {
    super(element, true);
    myValue = value;
  }

  public AntDomTargetReference(PsiElement element, GenericDomValue<TargetResolver.Result> value, TextRange range) {
    super(element, range, true);
    myValue = value;
  }

  public PsiElement resolve() {
    return ((PsiManagerEx)getElement().getManager()).getResolveCache().resolveWithCaching(this, MyResolver.INSTANCE, false, false);
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    // todo
    return super.handleElementRename(newElementName);
  }

  @NotNull
  public Object[] getVariants() {
    final TargetResolver.Result result = myValue.getValue();
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
      final LookupElementBuilder builder = LookupElementBuilder.create(s);
      final LookupElement element = AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE.applyPolicy(builder);
      resVariants.add(element);
    }
    return ContainerUtil.toArray(resVariants, new Object[resVariants.size()]);
  }

  private Set<String> getExistingNames() {
    final AntDomTarget hostTarget = myValue.getParentOfType(AntDomTarget.class, true);
    if (hostTarget == null) {
      return Collections.emptySet();
    }
    final Set<String> existing = new ArrayListSet<String>();
    final String selfName = hostTarget.getName().getStringValue();
    if (selfName != null) {
      existing.add(selfName);
    }
    final String dependsString = hostTarget.getDependsList().getStringValue();
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
    
    public PsiElement resolve(PsiReference psiReference, boolean incompleteCode) {
      final TargetResolver.Result result = ((AntDomTargetReference)psiReference).myValue.getValue();
      if (result == null) {
        return null;
      }
      final Pair<AntDomTarget,String> pair = result.getResolvedTarget(psiReference.getCanonicalText());
      final DomTarget domTarget = pair != null && pair.getFirst() != null ? DomTarget.getTarget(pair.getFirst()) : null;
      return domTarget != null? PomService.convertToPsi(domTarget) : null;
    }
  }
}
