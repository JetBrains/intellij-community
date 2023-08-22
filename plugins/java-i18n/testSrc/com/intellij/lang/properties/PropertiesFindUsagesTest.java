// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.codeInsight.JavaCodeInsightTestCase;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.findUsages.JavaClassFindUsagesOptions;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public class PropertiesFindUsagesTest extends JavaCodeInsightTestCase {
  private static final String BASE_PATH = "testData/findUsages/";
  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("java-i18n") + "/";
  }

  public void testFindUsages() throws Exception {
    configureByFile(BASE_PATH+"xx.properties", BASE_PATH);

    PsiReference[] references = findReferences();
    assertEquals(1, references.length);
    assertEquals("X.java", references[0].getElement().getContainingFile().getName());
  }
  public void testFindUsagesInPropValue() throws Exception {
    configureByFile(BASE_PATH+"Y.java", BASE_PATH);

    UsageInfo[] usages = findUsages();
    assertEquals(1, usages.length);
    PsiElement element = usages[0].getElement();
    assertEquals("xx.properties", element.getContainingFile().getName());
  }

  public void testFindUsagesInConditionalExpression() {
    configureByFiles(BASE_PATH, BASE_PATH + "conditional.properties", BASE_PATH + "Conditional.java");

    PsiElement element = myFile.findElementAt(myEditor.getCaretModel().getOffset());
    Property prop = PsiTreeUtil.getNonStrictParentOfType(element, Property.class);
    PsiReference[] usages = ReferencesSearch.search(prop).toArray(PsiReference.EMPTY_ARRAY);
    assertEquals(2, usages.length);
  }

  private static void processUsages(final PsiElement element,
                                    final FindUsagesOptions options,
                                    final Processor<? super UsageInfo> processor) {
    FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(element.getProject())).getFindUsagesManager();
    FindUsagesHandler handler = findUsagesManager.getFindUsagesHandler(element, false);
    assertNotNull(handler);
    handler.processElementUsages(element, processor, options);
  }

  private PsiReference[] findReferences() {
    PsiNamedElement namedElement = getElementAtCaret();

    GlobalSearchScope searchScope = GlobalSearchScope.allScope(myProject);

    return ReferencesSearch.search(namedElement, searchScope, false).toArray(PsiReference.EMPTY_ARRAY);
  }

  private UsageInfo[] findUsages() {
    PsiNamedElement namedElement = getElementAtCaret();
    JavaClassFindUsagesOptions options = new JavaClassFindUsagesOptions(getProject());
    options.isFieldsUsages=true;
    options.isMethodsUsages=true;
    options.isUsages=true;
    CommonProcessors.CollectProcessor<UsageInfo> processor = new CommonProcessors.CollectProcessor<>();
    processUsages(namedElement, options, processor);
    return processor.toArray(UsageInfo.EMPTY_ARRAY);
  }

  private PsiNamedElement getElementAtCaret() {
    PsiElement element = myFile.findElementAt(myEditor.getCaretModel().getOffset());
    PsiNamedElement namedElement = PsiTreeUtil.getParentOfType(element, PsiNamedElement.class);
    assertTrue("Cannot find element in caret", LanguageFindUsages.canFindUsagesFor(namedElement));
    return namedElement;
  }
}
