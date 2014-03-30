/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.intellij.plugins.intelliLang.inject.groovy;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.patterns.compiler.PatternClassBean;
import com.intellij.patterns.compiler.PatternCompilerFactory;
import com.intellij.psi.*;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.impl.search.LowLevelSearchUtil;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.TextOccurenceProcessor;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.StringSearcher;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;

import java.util.Collections;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class PatternEditorContextMembersProvider extends NonCodeMembersContributor {

  public static final Key<CachedValue<List<PsiElement>>> INJECTION_PARSED_CONTEXT = Key.create("INJECTION_PARSED_CONTEXT");

  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     PsiScopeProcessor processor,
                                     PsiElement place,
                                     ResolveState state) {
    final PsiFile file = place.getContainingFile().getOriginalFile();
    CachedValue<List<PsiElement>> value = file.getUserData(INJECTION_PARSED_CONTEXT);
    if (value == null) {
      final BaseInjection injection = file.getUserData(BaseInjection.INJECTION_KEY);
      final CachedValueProvider<List<PsiElement>> provider;
      if (injection == null) {
        provider = createDevProvider(file);
      }
      else {
        provider = createPatternProvider(injection, file);
      }
      if (provider == null) return;
      file.putUserData(INJECTION_PARSED_CONTEXT,
                       value = CachedValuesManager.getManager(file.getProject()).createCachedValue(provider, false));
    }
    final List<PsiElement> roots = value.getValue();
    for (PsiElement root : roots) {
      if (!root.processDeclarations(processor, state, null, place)) return;
    }
  }

  private static CachedValueProvider<List<PsiElement>> createPatternProvider(final BaseInjection injection, final PsiFile file) {
    return new CachedValueProvider<List<PsiElement>>() {
      @Override
      public Result<List<PsiElement>> compute() {
        return new Result<List<PsiElement>>(Collections.<PsiElement>singletonList(
          getRootByClasses(InjectorUtils.getPatternClasses(injection.getSupportId()), file.getProject())),
                                            ModificationTracker.NEVER_CHANGED);
      }
    };
  }

  private static PsiFile getRootByClasses(Class[] classes, Project project) {
    final String text = PatternCompilerFactory.getFactory().getPatternCompiler(classes).dumpContextDeclarations();
    return PsiFileFactory.getInstance(project).createFileFromText("context.groovy", GroovyFileType.GROOVY_FILE_TYPE, text);
  }

  @Nullable
  private static CachedValueProvider<List<PsiElement>> createDevProvider(final PsiFile file) {
    final XmlTag tag = getTagByInjectedFile(file);
    final XmlTag parentTag = tag == null ? null : tag.getParentTag();
    final String parentTagName = parentTag == null ? null : parentTag.getName();
    final String name = tag == null ? null : tag.getName();
    if ("place".equals(name) && "injection".equals(parentTagName) && parentTag != null) {
      return new CachedValueProvider<List<PsiElement>>() {
        @Override
        public Result<List<PsiElement>> compute() {
          final XmlTag tag = getTagByInjectedFile(file);
          final XmlTag parentTag = tag == null ? null : tag.getParentTag();
          if (parentTag == null) return Result.create(Collections.<PsiElement>emptyList(), file);
          return new Result<List<PsiElement>>(getRootsByClassNames(file, parentTag.getAttributeValue("injector-id")), parentTag.getContainingFile());
        }
      };
    }
    else if ("pattern".equals(name) && parentTag != null) {
      return new CachedValueProvider<List<PsiElement>>() {
        @Override
        public Result<List<PsiElement>> compute() {
          final XmlTag tag = getTagByInjectedFile(file);
          if (tag == null) return Result.create(Collections.<PsiElement>emptyList(), file);
          return new Result<List<PsiElement>>(getRootsByClassNames(file, tag.getAttributeValue("type")), tag.getContainingFile());
        }
      };
    }
    else return null;
  }

  @Nullable
  private static XmlTag getTagByInjectedFile(final PsiFile file) {
    final SmartPsiElementPointer pointer = file.getUserData(FileContextUtil.INJECTED_IN_ELEMENT);
    final PsiElement element = pointer == null? null : pointer.getElement();
    return element instanceof XmlText ? ((XmlText)element).getParentTag() : null;
  }

  private static List<PsiElement> getRootsByClassNames(PsiFile file, String type) {
    final List<PsiElement> roots = ContainerUtil.createLockFreeCopyOnWriteList();

    final Project project = file.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiClass beanClass = psiFacade.findClass(PatternClassBean.class.getName(), GlobalSearchScope.allScope(project));
    if (beanClass != null) {
      final GlobalSearchScope scope =
        GlobalSearchScope.getScopeRestrictedByFileTypes(GlobalSearchScope.allScope(project), StdFileTypes.XML);
      final TextOccurenceProcessor occurenceProcessor = new TextOccurenceProcessor() {
        @Override
        public boolean execute(PsiElement element, int offsetInElement) {
          final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
          final String className = tag == null ? null : tag.getAttributeValue("className");
          if (className != null && tag.getLocalName().endsWith("patternClass")) {
            ContainerUtil.addIfNotNull(psiFacade.findClass(className, GlobalSearchScope.allScope(project)), roots);
          }
          return true;
        }
      };
      final StringSearcher searcher = new StringSearcher("patternClass", true, true);
      CacheManager.SERVICE.getInstance(beanClass.getProject()).processFilesWithWord(new Processor<PsiFile>() {
        @Override
        public boolean process(PsiFile psiFile) {
          LowLevelSearchUtil
            .processElementsContainingWordInElement(occurenceProcessor, psiFile, searcher, true, new EmptyProgressIndicator());
          return true;
        }
      }, searcher.getPattern(), UsageSearchContext.IN_FOREIGN_LANGUAGES, scope, searcher.isCaseSensitive());
    }
    final Class[] classes = PatternCompilerFactory.getFactory().getPatternClasses(type);
    if (classes.length != 0) {
      roots.add(getRootByClasses(classes, project));
    }
    return roots;
  }

}
