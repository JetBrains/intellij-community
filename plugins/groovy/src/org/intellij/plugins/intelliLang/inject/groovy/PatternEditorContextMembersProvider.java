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
package org.intellij.plugins.intelliLang.inject.groovy;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
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
import com.intellij.psi.util.*;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SoftFactoryMap;
import com.intellij.util.text.StringSearcher;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;

import java.util.List;
import java.util.Set;

/**
 * @author Gregory.Shrago
 */
public class PatternEditorContextMembersProvider extends NonCodeMembersContributor {

  private static final Key<SoftFactoryMap<Class[], PsiFile>> PATTERN_INJECTION_CONTEXT = Key.create("PATTERN_INJECTION_CONTEXT");
  private static final Key<CachedValue<Set<String>>> PATTERN_CLASSES = Key.create("PATTERN_CLASSES");

  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     @NotNull final PsiScopeProcessor scopeProcessor,
                                     @NotNull final PsiElement place,
                                     @NotNull final ResolveState state) {
    final PsiFile file = place.getContainingFile().getOriginalFile();
    final BaseInjection injection = file.getUserData(BaseInjection.INJECTION_KEY);
    Processor<PsiElement> processor = new Processor<PsiElement>() {
      @Override
      public boolean process(PsiElement element) {
        return element.processDeclarations(scopeProcessor, state, null, place);
      }
    };
    if (injection == null) {
      processDevContext(file, processor);
    }
    else {
      processPatternContext(injection, file, processor);
    }
  }

  private static boolean processPatternContext(@NotNull BaseInjection injection,
                                               @NotNull PsiFile file,
                                               @NotNull Processor<PsiElement> processor) {
    return processor.process(getRootByClasses(file, InjectorUtils.getPatternClasses(injection.getSupportId())));
  }

  @NotNull
  private static PsiFile getRootByClasses(@NotNull PsiFile file, @NotNull Class[] classes) {
    final Project project = file.getProject();
    SoftFactoryMap<Class[], PsiFile> map = project.getUserData(PATTERN_INJECTION_CONTEXT);
    if (map == null) {
      map = new SoftFactoryMap<Class[], PsiFile>() {

        @Override
        protected PsiFile create(Class[] key) {
          String text = PatternCompilerFactory.getFactory().getPatternCompiler(key).dumpContextDeclarations();
          return PsiFileFactory.getInstance(project).createFileFromText("context.groovy", GroovyFileType.GROOVY_FILE_TYPE, text);
        }
      };
      project.putUserData(PATTERN_INJECTION_CONTEXT, map);
    }
    return map.get(classes);
  }

  private static boolean processDevContext(final PsiFile file, Processor<PsiElement> processor) {
    final XmlTag tag = getTagByInjectedFile(file);
    final XmlTag parentTag = tag == null ? null : tag.getParentTag();
    final String parentTagName = parentTag == null ? null : parentTag.getName();
    final String name = tag == null ? null : tag.getName();
    if ("place".equals(name) && "injection".equals(parentTagName)) {
      return processRootsByClassNames(file, parentTag.getAttributeValue("injector-id"), processor);
    }
    else if ("pattern".equals(name) && parentTag != null) {
      return processRootsByClassNames(file, tag.getAttributeValue("type"), processor);
    }
    return true;
  }

  @Nullable
  private static XmlTag getTagByInjectedFile(final PsiFile file) {
    final SmartPsiElementPointer pointer = file.getUserData(FileContextUtil.INJECTED_IN_ELEMENT);
    final PsiElement element = pointer == null? null : pointer.getElement();
    return element instanceof XmlText ? ((XmlText)element).getParentTag() : null;
  }

  private static boolean processRootsByClassNames(@NotNull PsiFile file, @Nullable String type, @NotNull Processor<PsiElement> processor) {
    Project project = file.getProject();
    Set<String> classNames = collectDevPatternClassNames(project);
    if (!classNames.isEmpty()) {
      JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      for (String className : classNames) {
        PsiClass patternClass = psiFacade.findClass(className, GlobalSearchScope.allScope(project));
        if (patternClass != null && !processor.process(patternClass)) return false;
      }
    }
    Class[] classes = StringUtil.isEmpty(type) ? ArrayUtil.EMPTY_CLASS_ARRAY : PatternCompilerFactory.getFactory().getPatternClasses(type);
    return classes.length == 0 || processor.process(getRootByClasses(file, classes));
  }

  private static Set<String> collectDevPatternClassNames(@NotNull final Project project) {
    CachedValue<Set<String>> cachedValue = project.getUserData(PATTERN_CLASSES);
    if (cachedValue == null) {
      cachedValue = CachedValuesManager.getManager(project).createCachedValue(new CachedValueProvider<Set<String>>() {
        @Nullable
        @Override
        public Result<Set<String>> compute() {
          return Result.create(calcDevPatternClassNames(project), PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
        }
      }, false);
      project.putUserData(PATTERN_CLASSES, cachedValue);
    }
    return cachedValue.getValue();
  }

  private static Set<String> calcDevPatternClassNames(@NotNull final Project project) {
    final List<String> roots = ContainerUtil.createLockFreeCopyOnWriteList();
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    PsiClass beanClass = psiFacade.findClass(PatternClassBean.class.getName(), GlobalSearchScope.allScope(project));
    if (beanClass != null) {
      GlobalSearchScope scope = GlobalSearchScope.getScopeRestrictedByFileTypes(GlobalSearchScope.allScope(project), StdFileTypes.XML);
      final TextOccurenceProcessor occurenceProcessor = new TextOccurenceProcessor() {
        @Override
        public boolean execute(@NotNull PsiElement element, int offsetInElement) {
          XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
          String className = tag == null ? null : tag.getAttributeValue("className");
          if (StringUtil.isNotEmpty(className) && tag.getLocalName().endsWith("patternClass")) {
            roots.add(className);
          }
          return true;
        }
      };
      final StringSearcher searcher = new StringSearcher("patternClass", true, true);
      CacheManager.SERVICE.getInstance(beanClass.getProject()).processFilesWithWord(new Processor<PsiFile>() {
        @Override
        public boolean process(PsiFile psiFile) {
          LowLevelSearchUtil.processElementsContainingWordInElement(occurenceProcessor, psiFile, searcher, true,
                                                                     new EmptyProgressIndicator());
          return true;
        }
      }, searcher.getPattern(), UsageSearchContext.IN_FOREIGN_LANGUAGES, scope, searcher.isCaseSensitive());
    }
    return ContainerUtil.newHashSet(roots);
  }

}
