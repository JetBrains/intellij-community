// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.debugger.fragments;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnAmbiguousClosureContainer;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.imports.*;
import org.jetbrains.plugins.groovy.lang.resolve.imports.impl.GroovyImportCollector;

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
  private final GroovyImportCollector myImportCollector = new GroovyImportCollector(this);
  private final ClearableLazyValue<GroovyFileImports> myFileImports = ClearableLazyValue.create(() -> myImportCollector.build());

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
    if (myImportCollector.isEmpty()) return "";

    final StringBuilder buffer = new StringBuilder();
    for (GroovyImport anImport : myImportCollector.getAllImports()) {
      if (anImport instanceof RegularImport) {
        buffer
          .append(((RegularImport)anImport).getClassFqn())
          .append(':')
          .append(((RegularImport)anImport).getName());
      }
      else if (anImport instanceof StaticImport) {
        buffer
          .append(((StaticImport)anImport).getClassFqn())
          .append('.')
          .append(((StaticImport)anImport).getMemberName())
          .append(':')
          .append(((StaticImport)anImport).getName());
      }
      else if (anImport instanceof GroovyStarImport) {
        buffer.append(((GroovyStarImport)anImport).getFqn());
      }
      else {
        PsiUtil.LOG.warn("Unsupported import. Class: " + anImport.getClass());
      }
      buffer.append(',');
    }

    buffer.deleteCharAt(buffer.length() - 1);
    return buffer.toString();
  }

  @Override
  public void addImportsFromString(String imports) {
    myFileImports.drop();
    for (String anImport : imports.split(",")) {
      addImport(anImport);
    }
  }

  private void addImport(@NotNull String importString) {
    int colon = importString.indexOf(':');
    if (colon >= 0) {
      final String qname = importString.substring(0, colon);
      final String importedName = importString.substring(colon + 1);
      final boolean isStatic = JavaPsiFacade.getInstance(getProject()).findClass(qname, getResolveScope()) == null;
      if (isStatic) {
        myImportCollector.addStaticImport(StringUtil.getPackageName(qname), StringUtil.getShortName(qname), importedName);
      }
      else {
        myImportCollector.addRegularImport(qname, importedName);
      }
    }
    else {
      final boolean isStatic = JavaPsiFacade.getInstance(getProject()).findClass(importString, getResolveScope()) != null;
      if (isStatic) {
        myImportCollector.addStaticStarImport(importString);
      }
      else {
        myImportCollector.addStarImport(importString);
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

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Override
  protected GroovyCodeFragment clone() {
    final GroovyCodeFragment clone = (GroovyCodeFragment)cloneImpl((FileElement)calcTreeElement().clone());
    clone.myOriginalFile = this;
    clone.myImportCollector.setFrom(myImportCollector);
    FileManager fileManager = ((PsiManagerEx)getManager()).getFileManager();
    SingleRootFileViewProvider cloneViewProvider = (SingleRootFileViewProvider)fileManager.createFileViewProvider(new LightVirtualFile(
      getName(),
      getLanguage(),
      getText()), false);
    cloneViewProvider.forceCachedPsi(clone);
    clone.myViewProvider = cloneViewProvider;
    return clone;
  }

  public void clearImports() {
    myFileImports.drop();
    myImportCollector.clear();
  }

  @Override
  protected GroovyFileImports getImports() {
    return myFileImports.getValue();
  }
}
