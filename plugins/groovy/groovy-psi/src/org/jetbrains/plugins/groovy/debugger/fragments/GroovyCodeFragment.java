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
package org.jetbrains.plugins.groovy.debugger.fragments;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnAmbiguousClosureContainer;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author ven
 */
public class GroovyCodeFragment extends GroovyFileImpl implements JavaCodeFragment, IntentionFilterOwner, GrUnAmbiguousClosureContainer {
  private PsiType myThisType;
  private PsiType mySuperType;
  private ExceptionHandler myExceptionChecker;
  private IntentionFilterOwner.IntentionActionsFilter myFilter;
  private GlobalSearchScope myResolveScope;

  /**
   * map from a class's imported name (e.g. its short name or alias) to its qualified name
   */
  private final LinkedHashMap<String, GrImportStatement> myPseudoImports = ContainerUtil.newLinkedHashMap();
  private final ArrayList<GrImportStatement> myOnDemandImports = ContainerUtil.newArrayList();
  private FileViewProvider myViewProvider = null;

  public GroovyCodeFragment(Project project, CharSequence text) {
    this(project, new LightVirtualFile("Dummy.groovy", GroovyFileType.GROOVY_FILE_TYPE, text));
  }

  public GroovyCodeFragment(Project project, VirtualFile virtualFile) {
    super(new SingleRootFileViewProvider(PsiManager.getInstance(project), virtualFile, true));
    ((SingleRootFileViewProvider)getViewProvider()).forceCachedPsi(this);
  }

  @Override
  public void setThisType(PsiType thisType) {
    myThisType = thisType;
  }

  @Override
  public PsiType getSuperType() {
    return mySuperType;
  }

  @Override
  public void setSuperType(PsiType superType) {
    mySuperType = superType;
  }

  @Override
  @NotNull
  public FileViewProvider getViewProvider() {
    if (myViewProvider != null) return myViewProvider;
    return super.getViewProvider();
  }

  /**
   * @return list of imports in format "qname[:imported_name](,qname[:imported_name])*"
   */
  @Override
  public String importsToString() {
    if (myPseudoImports.isEmpty()) return "";

    StringBuilder buffer = new StringBuilder();
    for (Map.Entry<String, GrImportStatement> entry : myPseudoImports.entrySet()) {
      final String importedName = entry.getKey();
      final GrImportStatement anImport = entry.getValue();


      //buffer.append(anImport.isStatic() ? "+" : "-");
      final String qname = anImport.getImportReference().getClassNameText();

      buffer.append(qname);
      buffer.append(':').append(importedName);
      buffer.append(',');
    }

    for (GrImportStatement anImport : myOnDemandImports) {
      //buffer.append(anImport.isStatic() ? "+" : "-");

      String packName = anImport.getImportReference().getClassNameText();
      buffer.append(packName);
      buffer.append(',');
    }
    buffer.deleteCharAt(buffer.length() - 1);
    return buffer.toString();
  }

  @Override
  public void addImportsFromString(String imports) {
    for (String anImport : imports.split(",")) {
      int colon = anImport.indexOf(':');

      if (colon >= 0) {
        String qname = anImport.substring(0, colon);
        String importedName = anImport.substring(colon + 1);
        myPseudoImports.put(importedName, createSingleImport(qname, importedName));
      }
      else {
        myOnDemandImports.add(createImportOnDemand(anImport));
      }
    }
  }

  @Override
  public void setVisibilityChecker(JavaCodeFragment.VisibilityChecker checker) {
  }

  @Override
  public VisibilityChecker getVisibilityChecker() {
    return VisibilityChecker.EVERYTHING_VISIBLE;
  }

  @Override
  public void setExceptionHandler(ExceptionHandler checker) {
    myExceptionChecker = checker;
  }

  @Override
  public ExceptionHandler getExceptionHandler() {
    return myExceptionChecker;
  }

  @Override
  public void setIntentionActionsFilter(@NotNull IntentionActionsFilter filter) {
    myFilter = filter;
  }

  @Override
  public IntentionActionsFilter getIntentionActionsFilter() {
    return myFilter;
  }

  @Override
  public void forceResolveScope(GlobalSearchScope scope) {
    myResolveScope = scope;
  }

  @Override
  public GlobalSearchScope getForcedResolveScope() {
    return myResolveScope;
  }

  @Override
  public boolean importClass(PsiClass aClass) {
    return false;
  }

  @Override
  public PsiType getThisType() {
    return myThisType;
  }

  @Override
  protected boolean processImports(PsiScopeProcessor processor,
                                   @NotNull ResolveState state,
                                   PsiElement lastParent,
                                   @NotNull PsiElement place,
                                   @NotNull GrImportStatement[] importStatements,
                                   boolean onDemand) {
    if (!super.processImports(processor, state, lastParent, place, importStatements, onDemand)) {
      return false;
    }
    if (!processPseudoImports(processor, state, lastParent, place, onDemand)) {
      return false;
    }

    return true;
  }

  @Override
  protected GroovyCodeFragment clone() {
    final GroovyCodeFragment clone = (GroovyCodeFragment)cloneImpl((FileElement)calcTreeElement().clone());
    clone.myOriginalFile = this;
    clone.myPseudoImports.putAll(myPseudoImports);
    FileManager fileManager = ((PsiManagerEx)getManager()).getFileManager();
    SingleRootFileViewProvider cloneViewProvider = (SingleRootFileViewProvider)fileManager.createFileViewProvider(new LightVirtualFile(
      getName(),
      getLanguage(),
      getText()), false);
    cloneViewProvider.forceCachedPsi(clone);
    clone.myViewProvider = cloneViewProvider;
    return clone;
  }

  protected boolean processPseudoImports(PsiScopeProcessor processor,
                                         @NotNull ResolveState state,
                                         PsiElement lastParent,
                                         PsiElement place,
                                         boolean onDemand) {
    if (onDemand) {
      if (!processImportsOnDemand(processor, state, lastParent, place)) {
        return false;
      }
    }
    else {
      if (!processSingleImports(processor, state, lastParent, place)) {
        return false;
      }
    }
    return true;
  }

  private boolean processImportsOnDemand(PsiScopeProcessor processor, ResolveState state, PsiElement parent, PsiElement place) {
    for (GrImportStatement anImport : myOnDemandImports) {
      if (!anImport.processDeclarations(processor, state, parent, place)) {
        return false;
      }
    }
    return true;
  }

  private boolean processSingleImports(PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, PsiElement place) {
    NameHint nameHint = processor.getHint(NameHint.KEY);
    String name = nameHint != null ? nameHint.getName(state) : null;

    if (name != null) {
      final GrImportStatement anImport = myPseudoImports.get(name);
      if (anImport != null) {
        if (!anImport.processDeclarations(processor, state, lastParent, place)) {
          return false;
        }
      }
    }
    else {
      for (GrImportStatement anImport : myPseudoImports.values()) {
        if (!anImport.processDeclarations(processor, state, lastParent, place)) {
          return false;
        }
      }
    }
    return true;
  }

  @Nullable
  private GrImportStatement createImportOnDemand(@NotNull String qname) {
    final PsiClass aClass = JavaPsiFacade.getInstance(getProject()).findClass(qname, getResolveScope());
    final boolean isStatic = aClass != null;

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
    try {
      return factory.createImportStatement(qname, isStatic, true, null, this);
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  @Nullable
  private GrImportStatement createSingleImport(@NotNull String qname, @Nullable String importedName) {
    final PsiClass aClass = JavaPsiFacade.getInstance(getProject()).findClass(qname, getResolveScope());
    final boolean isStatic = aClass == null;

    final String className = PsiNameHelper.getShortClassName(qname);
    final String alias = importedName == null || className.equals(importedName) ? null : importedName;
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
    try {
      return factory.createImportStatement(qname, isStatic, false, alias, this);
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  public void clearImports() {
    myPseudoImports.clear();
    myOnDemandImports.clear();
  }
}
