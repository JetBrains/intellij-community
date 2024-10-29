// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.debugger;

import com.intellij.debugger.NoDataException;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.jdi.VirtualMachineProxy;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ThreeState;
import com.intellij.xdebugger.frame.XStackFrame;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.request.ClassPrepareRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.extensions.debugger.ScriptPositionManagerHelper;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSwitchElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSwitchExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.stubs.GroovyShortNamesCache;

import java.util.*;

public class GroovyPositionManager extends PositionManagerEx {
  private static final Logger LOG = Logger.getInstance(PositionManagerImpl.class);

  private final DebugProcess myDebugProcess;
  private static final Set<FileType> ourFileTypes = Collections.singleton(GroovyFileType.GROOVY_FILE_TYPE);

  public GroovyPositionManager(DebugProcess debugProcess) {
    myDebugProcess = debugProcess;
  }

  public DebugProcess getDebugProcess() {
    return myDebugProcess;
  }

  @Override
  @NotNull
  public List<Location> locationsOfLine(@NotNull ReferenceType type, @NotNull SourcePosition position) throws NoDataException {
    checkGroovyFile(position);
    try {
      if (LOG.isDebugEnabled()) {
        LOG.debug("locationsOfLine: " + type + "; " + position);
      }
      int line = position.getLine() + 1;
      List<Location> locations = getDebugProcess().getVirtualMachineProxy().versionHigher("1.4")
                                 ? DebuggerUtilsAsync.locationsOfLineSync(type, DebugProcess.JAVA_STRATUM, null, line)
                                 : DebuggerUtilsAsync.locationsOfLineSync(type, line);
      if (locations == null || locations.isEmpty()) throw NoDataException.INSTANCE;
      return locations;
    }
    catch (AbsentInformationException e) {
      throw NoDataException.INSTANCE;
    }
  }

  @Override
  public ThreeState evaluateCondition(@NotNull EvaluationContext context,
                                      @NotNull StackFrameProxyImpl frame,
                                      @NotNull Location location,
                                      @NotNull String expression) {
    return ThreeState.UNSURE;
  }

  @Override
  public @Nullable XStackFrame createStackFrame(@NotNull StackFrameDescriptorImpl descriptor) {
    if (isInGroovyFile(descriptor.getLocation()) != ThreeState.YES) {
      return null;
    }
    return new GroovyStackFrame(descriptor, true);
  }

  private static ThreeState isInGroovyFile(@Nullable Location location) {
    if (location != null) {
      var refType = location.declaringType();
      try {
        String safeName = refType.sourceName();
        FileType fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(safeName);
        if (fileType == UnknownFileType.INSTANCE) {
          return ThreeState.UNSURE;
        }
        if (fileType instanceof LanguageFileType languageFileType) {
          if (languageFileType.getLanguage() == GroovyLanguage.INSTANCE) {
            return ThreeState.YES;
          }
        }
      } catch (AbsentInformationException ignore) {
      }
    }
    return ThreeState.NO;
  }

  @Nullable
  private static GroovyPsiElement findReferenceTypeSourceImage(SourcePosition position) {
    PsiFile file = position.getFile();
    if (!(file instanceof GroovyFileBase)) return null;
    PsiElement element = file.findElementAt(position.getOffset());
    if (element == null) return null;
    return getEnclosingPsiForElement(element);
  }

  /**
   * In Groovy, there are some transformations performed before compiling or interpreting the source code.
   * <br>
   * First, all closures and lambdas are transformed to a local class extending {@code groovy.lang.Closure}. Therefore, code inside closures
   * does not correspond to the class of the methods where the closure is defined.
   * <br>
   * Second, there is no such thing as a "switch expression".
   * The following
   * <code>
   * <pre>
   * switch(10) {
   *   case 20 -> 30
   *   case 30 -> {
   *      40
   *   }
   * }
   * </pre>
   * </code>
   * is transformed to
   * <code>
   * <pre>
   * { ->
   *   switch(10) {
   *     case 20: return 30
   *     case 30: return {->
   *        return 40
   *     }()
   *   }
   * }()
   * </pre>
   * </code>
   * So the code inside switch expressions corresponds to a separate local class too.
   */
  private static GroovyPsiElement getEnclosingPsiForElement(PsiElement element) {
    while (true) {
      GroovyPsiElement parent = PsiTreeUtil.getParentOfType(element, GrSwitchExpression.class, GrFunctionalExpression.class, GrTypeDefinition.class);
      if (!(parent instanceof GrSwitchElement && PsiUtil.isPlainSwitchStatement((GrSwitchElement)parent))) {
        return parent;
      } else {
        element = parent;
      }
    }

  }

  @Nullable
  private static PsiClass findEnclosingTypeDefinition(SourcePosition position) {
    PsiFile file = position.getFile();
    if (!(file instanceof GroovyFileBase)) return null;
    PsiElement element = file.findElementAt(position.getOffset());
    while (true) {
      element = PsiTreeUtil.getParentOfType(element, GrTypeDefinition.class, GroovyFileBase.class);
      if (element instanceof GroovyFileBase) {
        return ((GroovyFileBase)element).getScriptClass();
      }
      else if (element instanceof GrTypeDefinition && !((GrTypeDefinition)element).isAnonymous()) {
        return (GrTypeDefinition)element;
      }
    }
  }

  private static void checkGroovyFile(@NotNull SourcePosition position) throws NoDataException {
    if (!(position.getFile() instanceof GroovyFileBase)) {
      throw NoDataException.INSTANCE;
    }
  }

  @Override
  public ClassPrepareRequest createPrepareRequest(@NotNull final ClassPrepareRequestor requestor, @NotNull final SourcePosition position)
    throws NoDataException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("createPrepareRequest: " + position);
    }
    checkGroovyFile(position);

    String qName = getOuterClassName(position);
    if (qName != null) {
      return myDebugProcess.getRequestsManager().createClassPrepareRequest(requestor, qName);
    }

    qName = findEnclosingName(position);

    if (qName == null) throw NoDataException.INSTANCE;
    ClassPrepareRequestor waitRequestor = new ClassPrepareRequestor() {
      @Override
      public void processClassPrepare(DebugProcess debuggerProcess, ReferenceType referenceType) {
        final CompoundPositionManager positionManager = ((DebugProcessImpl)debuggerProcess).getPositionManager();
        if (!positionManager.locationsOfLine(referenceType, position).isEmpty()) {
          requestor.processClassPrepare(debuggerProcess, referenceType);
        }
      }
    };
    return myDebugProcess.getRequestsManager().createClassPrepareRequest(waitRequestor, qName + "$*");
  }

  @Nullable
  private static String findEnclosingName(@NotNull final SourcePosition position) {
    return ReadAction.compute(()->{
      PsiClass typeDefinition = findEnclosingTypeDefinition(position);
      if (typeDefinition != null) {
        return getClassNameForJvm(typeDefinition);
      }
      return getScriptQualifiedName(position);
    });
  }

  @Nullable
  private static String getOuterClassName(final SourcePosition position) {
    return ReadAction.compute(()->{
      GroovyPsiElement sourceImage = findReferenceTypeSourceImage(position);
      if (sourceImage instanceof GrTypeDefinition) {
        return getClassNameForJvm((GrTypeDefinition)sourceImage);
      }
      else if (sourceImage == null) {
        return getScriptQualifiedName(position);
      }
      return null;
    });
  }

  @Nullable
  private static String getClassNameForJvm(@NotNull final PsiClass typeDefinition) {
    String suffix = typeDefinition instanceof GrTypeDefinition && ((GrTypeDefinition)typeDefinition).isTrait() ? "$Trait$Helper" : "";
    final PsiClass psiClass = typeDefinition.getContainingClass();
    if (psiClass != null) {
      String parent = getClassNameForJvm(psiClass);
      return parent == null ? null : parent + "$" + typeDefinition.getName() + suffix;
    }

    PsiFile file = typeDefinition.getContainingFile();
    if (file instanceof GroovyFile && ((GroovyFile)file).isScript()) {
      for (ScriptPositionManagerHelper helper : ScriptPositionManagerHelper.EP_NAME.getExtensions()) {
        String s = helper.isAppropriateScriptFile((GroovyFile)file) ? helper.customizeClassName(typeDefinition) : null;
        if (s != null) {
          return s;
        }
      }
    }

    String qname = typeDefinition.getQualifiedName();
    return qname == null ? null : qname + suffix;
  }

  @Nullable
  private static String getScriptQualifiedName(@NotNull SourcePosition position) {
    PsiFile file = position.getFile();
    if (file instanceof GroovyFile) {
      return getScriptFQName((GroovyFile)file);
    }
    return null;
  }

  @Override
  public SourcePosition getSourcePosition(@Nullable final Location location) throws NoDataException {
    if (location == null) throw NoDataException.INSTANCE;
    if (isInGroovyFile(location) == ThreeState.NO) throw NoDataException.INSTANCE;

    int lineNumber = calcLineIndex(location);
    if (lineNumber < 0) throw NoDataException.INSTANCE;

    if (LOG.isDebugEnabled()) {
      LOG.debug("getSourcePosition: " + location);
    }
    PsiFile psiFile = getPsiFileByLocation(getDebugProcess().getProject(), location);
    if (psiFile == null) throw NoDataException.INSTANCE;

    return SourcePosition.createFromLine(psiFile, lineNumber);
  }

  private int calcLineIndex(Location location) {
    LOG.assertTrue(myDebugProcess != null);
    if (location == null) return -1;
    return DebuggerUtilsEx.getLineNumber(location, true);
  }

  @Nullable
  private PsiFile getPsiFileByLocation(@NotNull final Project project, @Nullable final Location location) {
    if (location == null) return null;

    final ReferenceType refType = location.declaringType();
    if (refType == null) return null;

    final String originalQName = refType.name().replace('/', '.');
    int dollar = originalQName.indexOf('$');
    String runtimeName = dollar >= 0 ? originalQName.substring(0, dollar) : originalQName;
    String qName = getOriginalQualifiedName(refType, runtimeName);

    GlobalSearchScope searchScope = myDebugProcess.getSearchScope();
    GroovyShortNamesCache cache = GroovyShortNamesCache.getGroovyShortNamesCache(project);
    try {
      List<PsiClass> classes = cache.getClassesByFQName(qName, searchScope, true);
      if (classes.isEmpty()) {
        classes = cache.getClassesByFQName(qName, searchScope, false);
      }
      if (classes.isEmpty()) {
        classes = cache.getClassesByFQName(qName, GlobalSearchScope.projectScope(project), false);
      }
      if (classes.isEmpty()) {
        classes = cache.getClassesByFQName(qName, addModuleContent(searchScope), false);
      }
      if (!classes.isEmpty()) {
        classes.sort(PsiClassUtil.createScopeComparator(searchScope));
        PsiClass clazz = classes.get(0);
        if (clazz != null) return clazz.getContainingFile();
      }
    }
    catch (ProcessCanceledException | IndexNotReadyException e) {
      return null;
    }

    return getExtraScriptIfNotFound(project, refType, runtimeName, searchScope);
  }

  @Nullable
  private static PsiFile getExtraScriptIfNotFound(@NotNull Project project,
                                                  @NotNull ReferenceType refType,
                                                  @NotNull String runtimeName,
                                                  @NotNull GlobalSearchScope searchScope) {
    for (ScriptPositionManagerHelper helper : ScriptPositionManagerHelper.EP_NAME.getExtensions()) {
      if (helper.isAppropriateRuntimeName(runtimeName)) {
        PsiFile file = helper.getExtraScriptIfNotFound(refType, runtimeName, project, searchScope);
        if (file != null) return file;
      }
    }
    return null;
  }

  private static GlobalSearchScope addModuleContent(GlobalSearchScope scope) {
    if (scope instanceof ModuleWithDependenciesScope) {
      Module module = ((ModuleWithDependenciesScope)scope).getModule();
      if (!module.isDisposed()) {
        return scope.uniteWith(module.getModuleContentWithDependenciesScope());
      }
    }
    return scope;
  }

  private static String getOriginalQualifiedName(@NotNull ReferenceType refType, @NotNull String runtimeName) {
    for (ScriptPositionManagerHelper helper : ScriptPositionManagerHelper.EP_NAME.getExtensions()) {
      if (helper.isAppropriateRuntimeName(runtimeName)) {
        String originalScriptName = helper.getOriginalScriptName(refType, runtimeName);
        if (originalScriptName != null) return originalScriptName;
      }
    }
    return runtimeName;
  }

  @Override
  @NotNull
  public List<ReferenceType> getAllClasses(@NotNull final SourcePosition position) throws NoDataException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("getAllClasses: " + position);
    }

    checkGroovyFile(position);
    List<ReferenceType> result = ReadAction.compute(() -> {
      GroovyPsiElement sourceImage = findReferenceTypeSourceImage(position);

      if (sourceImage instanceof GrTypeDefinition && !((GrTypeDefinition)sourceImage).isAnonymous()) {
        String qName = getClassNameForJvm((GrTypeDefinition)sourceImage);
        if (qName != null) return myDebugProcess.getVirtualMachineProxy().classesByName(qName);
      }
      else if (sourceImage == null) {
        final String scriptName = getScriptQualifiedName(position);
        if (scriptName != null) return myDebugProcess.getVirtualMachineProxy().classesByName(scriptName);
      }
      else {
        String enclosingName = findEnclosingName(position);
        if (enclosingName == null) return null;

        final List<ReferenceType> outers = myDebugProcess.getVirtualMachineProxy().classesByName(enclosingName);
        final List<ReferenceType> result1 = new ArrayList<>(outers.size());
        for (ReferenceType outer : outers) {
          final ReferenceType nested = findNested(outer, sourceImage, position);
          if (nested != null) {
            result1.add(nested);
          }
        }
        return result1;
      }
      return null;
    });

    if (LOG.isDebugEnabled()) {
      LOG.debug("getAllClasses = " + result);
    }
    if (result == null) throw NoDataException.INSTANCE;
    return result;
  }

  @Nullable
  private static String getScriptFQName(@NotNull GroovyFile groovyFile) {
    String packageName = groovyFile.getPackageName();
    String fileName = getRuntimeScriptName(groovyFile);
    return StringUtil.getQualifiedName(packageName, fileName);
  }

  @NotNull
  private static String getRuntimeScriptName(@NotNull GroovyFile groovyFile) {
    if (groovyFile.isScript()) {
      for (ScriptPositionManagerHelper helper : ScriptPositionManagerHelper.EP_NAME.getExtensions()) {
        if (helper.isAppropriateScriptFile(groovyFile)) {
          String runtimeScriptName = helper.getRuntimeScriptName(groovyFile);
          if (runtimeScriptName != null) return runtimeScriptName;
        }
      }
    }
    VirtualFile vFile = groovyFile.getVirtualFile();
    assert vFile != null;
    return vFile.getNameWithoutExtension();
  }

  @Nullable
  private ReferenceType findNested(ReferenceType fromClass, final GroovyPsiElement toFind, SourcePosition classPosition) {
    final VirtualMachineProxy vmProxy = myDebugProcess.getVirtualMachineProxy();
    if (fromClass.isPrepared()) {

      final List<ReferenceType> nestedTypes = vmProxy.nestedTypes(fromClass);

      for (ReferenceType nested : nestedTypes) {
        final ReferenceType found = findNested(nested, toFind, classPosition);
        if (found != null) {
          return found;
        }
      }

      try {
        final int lineNumber = classPosition.getLine() + 1;
        if (!DebuggerUtilsAsync.locationsOfLineSync(fromClass, lineNumber).isEmpty()) {
          return fromClass;
        }
        for (Location location : DebuggerUtilsAsync.allLineLocationsSync(fromClass)) {
          final SourcePosition candidateFirstPosition = SourcePosition.createFromLine(
            toFind.getContainingFile(), location.lineNumber() - 1
          );
          if (toFind.equals(findReferenceTypeSourceImage(candidateFirstPosition))) {
            return fromClass;
          }
          break; // isApplicable only the first location
        }
      }
      catch (AbsentInformationException ignored) {
      }
    }
    return null;
  }

  @NotNull
  @Override
  public Set<? extends FileType> getAcceptedFileTypes() {
    var result = new HashSet<FileType>();
    ScriptPositionManagerHelper.EP_NAME.forEachExtensionSafe(ext -> result.addAll(ext.getAcceptedFileTypes()));
    result.addAll(ourFileTypes);
    return result;
  }
}