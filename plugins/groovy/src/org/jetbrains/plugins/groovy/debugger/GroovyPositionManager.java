/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.debugger;

import com.intellij.debugger.NoDataException;
import com.intellij.debugger.PositionManager;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.CompoundPositionManager;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.jdi.VirtualMachineProxy;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.request.ClassPrepareRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.debugger.ScriptPositionManagerHelper;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.stubs.GroovyShortNamesCache;

import java.util.ArrayList;
import java.util.List;

public class GroovyPositionManager implements PositionManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.PositionManagerImpl");

  private final DebugProcess myDebugProcess;

  public GroovyPositionManager(DebugProcess debugProcess) {
    myDebugProcess = debugProcess;
  }

  public DebugProcess getDebugProcess() {
    return myDebugProcess;
  }

  @NotNull
  public List<Location> locationsOfLine(ReferenceType type, SourcePosition position) throws NoDataException {
    try {
      int line = position.getLine() + 1;
      List<Location> locations = getDebugProcess().getVirtualMachineProxy().versionHigher("1.4")
                                 ? type.locationsOfLine(DebugProcess.JAVA_STRATUM, null, line)
                                 : type.locationsOfLine(line);
      if (locations == null || locations.isEmpty()) throw new NoDataException();
      return locations;
    }
    catch (AbsentInformationException e) {
      throw new NoDataException();
    }
  }

  @Nullable
  private static GroovyPsiElement findReferenceTypeSourceImage(SourcePosition position) {
    PsiFile file = position.getFile();
    if (!(file instanceof GroovyFileBase)) return null;
    PsiElement element = file.findElementAt(position.getOffset());
    if (element == null) return null;
    return PsiTreeUtil.getParentOfType(element, GrClosableBlock.class, GrTypeDefinition.class);
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

  public ClassPrepareRequest createPrepareRequest(final ClassPrepareRequestor requestor, final SourcePosition position)
    throws NoDataException {
    String qName = getOuterClassName(position);
    if (qName != null) {
      return myDebugProcess.getRequestsManager().createClassPrepareRequest(requestor, qName);
    }

    qName = findEnclosingName(position);

    if (qName == null) throw new NoDataException();
    ClassPrepareRequestor waitRequestor = new ClassPrepareRequestor() {
      public void processClassPrepare(DebugProcess debuggerProcess, ReferenceType referenceType) {
        final CompoundPositionManager positionManager = ((DebugProcessImpl)debuggerProcess).getPositionManager();
        if (positionManager.locationsOfLine(referenceType, position).size() > 0) {
          requestor.processClassPrepare(debuggerProcess, referenceType);
        }
      }
    };
    return myDebugProcess.getRequestsManager().createClassPrepareRequest(waitRequestor, qName + "$*");
  }

  @Nullable
  private static String findEnclosingName(final SourcePosition position) {
    AccessToken accessToken = ApplicationManager.getApplication().acquireReadActionLock();

    try {
      PsiClass typeDefinition = findEnclosingTypeDefinition(position);
      if (typeDefinition != null) {
        return getClassNameForJvm(typeDefinition);
      }
      return getScriptQualifiedName(position);
    }
    finally {
      accessToken.finish();
    }
  }

  @Nullable
  private static String getOuterClassName(final SourcePosition position) {
    AccessToken accessToken = ApplicationManager.getApplication().acquireReadActionLock();

    try {
      GroovyPsiElement sourceImage = findReferenceTypeSourceImage(position);
      if (sourceImage instanceof GrTypeDefinition) {
        return getClassNameForJvm((GrTypeDefinition)sourceImage);
      } else if (sourceImage == null) {
        return getScriptQualifiedName(position);
      }
      return null;
    }
    finally {
      accessToken.finish();
    }
  }

  @Nullable
  private static String getClassNameForJvm(final PsiClass typeDefinition) {
    final PsiClass psiClass = typeDefinition.getContainingClass();
    if (psiClass != null) {
      return getClassNameForJvm(psiClass) + "$" + typeDefinition.getName();
    }

    for (ScriptPositionManagerHelper helper : ScriptPositionManagerHelper.EP_NAME.getExtensions()) {
      final String s = helper.customizeClassName(typeDefinition);
      if (s != null) {
        return s;
      }
    }

    return typeDefinition.getQualifiedName();
  }

  @Nullable
  private static String getScriptQualifiedName(SourcePosition position) {
    PsiFile file = position.getFile();
    if (file instanceof GroovyFile) {
      return getScriptFQName((GroovyFile)file);
    }
    return null;
  }

  public SourcePosition getSourcePosition(final Location location) throws NoDataException {
    if (location == null) throw new NoDataException();

    PsiFile psiFile = getPsiFileByLocation(getDebugProcess().getProject(), location);
    if (psiFile == null) throw new NoDataException();

    int lineNumber = calcLineIndex(location);
    if (lineNumber < 0) throw new NoDataException();
    return SourcePosition.createFromLine(psiFile, lineNumber);
  }

  private int calcLineIndex(Location location) {
    LOG.assertTrue(myDebugProcess != null);
    if (location == null) return -1;

    try {
      return location.lineNumber() - 1;
    }
    catch (InternalError e) {
      return -1;
    }
  }

  @Nullable
  private PsiFile getPsiFileByLocation(final Project project, final Location location) {
    if (location == null) return null;

    final ReferenceType refType = location.declaringType();
    if (refType == null) return null;

    final String originalQName = refType.name().replace('/', '.');
    int dollar = originalQName.indexOf('$');
    String runtimeName = dollar >= 0 ? originalQName.substring(0, dollar) : originalQName;
    String qName = getOriginalQualifiedName(refType, runtimeName);

    GlobalSearchScope searchScope = addModuleContent(myDebugProcess.getSearchScope());
    try {
      final List<PsiClass> classes = GroovyShortNamesCache.getGroovyShortNamesCache(project).getClassesByFQName(qName, searchScope);
      PsiClass clazz = classes.size() == 1 ? classes.get(0) : null;
      if (clazz != null) return clazz.getContainingFile();
    }
    catch (ProcessCanceledException e) {
      return null;
    }
    catch (IndexNotReadyException e) {
      return null;
    }

    return getExtraScriptIfNotFound(project, refType, runtimeName, searchScope);
  }

  @Nullable
  private static PsiFile getExtraScriptIfNotFound(Project project,
                                                  ReferenceType refType,
                                                  String runtimeName,
                                                  GlobalSearchScope searchScope) {
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
      return scope.uniteWith(((ModuleWithDependenciesScope)scope).getModule().getModuleContentWithDependenciesScope());
    }
    return scope;
  }

  private static String getOriginalQualifiedName(ReferenceType refType, String runtimeName) {
    for (ScriptPositionManagerHelper helper : ScriptPositionManagerHelper.EP_NAME.getExtensions()) {
      if (helper.isAppropriateRuntimeName(runtimeName)) {
        return helper.getOriginalScriptName(refType, runtimeName);
      }
    }
    return runtimeName;
  }

  @NotNull
  public List<ReferenceType> getAllClasses(final SourcePosition position) throws NoDataException {
    List<ReferenceType> result = ApplicationManager.getApplication().runReadAction(new Computable<List<ReferenceType>>() {
      public List<ReferenceType> compute() {
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
          final List<ReferenceType> result = new ArrayList<ReferenceType>(outers.size());
          for (ReferenceType outer : outers) {
            final ReferenceType nested = findNested(outer, sourceImage, position);
            if (nested != null) {
              result.add(nested);
            }
          }
          return result;
        }
        return null;
      }
    });

    if (result == null) throw new NoDataException();
    return result;
  }

  private static String getScriptFQName(GroovyFile groovyFile) {
    String packageName = groovyFile.getPackageName();
    String fileName = getRuntimeScriptName(groovyFile);
    return packageName.length() > 0 ? packageName + "." + fileName : fileName;
  }

  private static String getRuntimeScriptName(GroovyFile groovyFile) {
    VirtualFile vFile = groovyFile.getVirtualFile();
    assert vFile != null;
    String plainName = vFile.getNameWithoutExtension();
    if (groovyFile.isScript()) {
      for (ScriptPositionManagerHelper helper : ScriptPositionManagerHelper.EP_NAME.getExtensions()) {
        if (helper.isAppropriateScriptFile(groovyFile)) {
          return helper.getRuntimeScriptName(plainName, groovyFile);
        }
      }
    }
    return plainName;
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
        if (fromClass.locationsOfLine(lineNumber).size() > 0) {
          return fromClass;
        }
        //noinspection LoopStatementThatDoesntLoop
        for (Location location : fromClass.allLineLocations()) {
          final SourcePosition candidateFirstPosition = SourcePosition.createFromLine(toFind.getContainingFile(), location.lineNumber() - 1)
            ;
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

}