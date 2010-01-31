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

package org.jetbrains.plugins.groovy.compiler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.containers.FactoryMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyFileTypeLoader;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.util.*;

/**
 * @author Dmitry.Krasilschikov
 */

public class GroovyCompiler extends GroovyCompilerBase {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.compiler.GroovyCompiler");

  public GroovyCompiler(Project project) {
    super(project);
  }

  @NotNull
  public String getDescription() {
    return "groovy compiler";
  }

  @Override
  protected void compileFiles(final CompileContext context, final Module module, List<VirtualFile> toCompile, OutputSink sink, boolean tests) {
    final Set<VirtualFile> allToCompile = new LinkedHashSet<VirtualFile>(toCompile);

    // groovyc may fail if we don't also recompile files like B such that A depends on B and B depends on C, where A & C \in toCompile
    // see http://jira.codehaus.org/browse/GROOVY-4024
    // this is important only if >1 files have been changed
    if (toCompile.size() > 1 && !"false".equals(System.getProperty("compile.groovy.dependencies", "true"))) {
      context.getProgressIndicator().checkCanceled();
      context.getProgressIndicator().setText("Enumerating Groovy classes...");

      Set<VirtualFile> groovyFiles = enumerateGroovyFiles(module);

      if (toCompile.size() < groovyFiles.size()) {
        context.getProgressIndicator().checkCanceled();
        context.getProgressIndicator().setText("Processing Groovy dependencies...");

        addIntermediateGroovyClasses(allToCompile, groovyFiles);
      }
    }

    context.getProgressIndicator().checkCanceled();
    context.getProgressIndicator().setText(GroovycOSProcessHandler.GROOVY_COMPILER_IN_OPERATION);

    runGroovycCompiler(context, module, new ArrayList<VirtualFile>(allToCompile), false, getMainOutput(context, module, tests), sink, tests);
  }

  private void addIntermediateGroovyClasses(Set<VirtualFile> allToCompile, final Set<VirtualFile> groovyFiles) {
    final Set<VirtualFile> initialFiles = new THashSet<VirtualFile>(allToCompile);

    final THashSet<VirtualFile> visited = new THashSet<VirtualFile>();
    for (VirtualFile aClass : initialFiles) {
      if (visited.add(aClass)) {
        goForIntermediateFiles(aClass, allToCompile, new FactoryMap<VirtualFile, Set<VirtualFile>>() {
          @Override
          protected Set<VirtualFile> create(final VirtualFile key) {
            return ApplicationManager.getApplication().runReadAction(new Computable<Set<VirtualFile>>() {
              public Set<VirtualFile> compute() {
                return calcCodeReferenceDependencies(key, groovyFiles);
              }
            });
          }
        }, visited);
      }
    }
  }

  private Set<VirtualFile> enumerateGroovyFiles(final Module module) {
    final Set<VirtualFile> moduleClasses = new THashSet<VirtualFile>();
    ModuleRootManager.getInstance(module).getFileIndex().iterateContent(new ContentIterator() {
      public boolean processFile(final VirtualFile vfile) {
        if (!vfile.isDirectory() &&
            GroovyFileType.GROOVY_FILE_TYPE.equals(vfile.getFileType()) &&
            PsiManager.getInstance(myProject).findFile(vfile) instanceof GroovyFile) {
          moduleClasses.add(vfile);
        }
        return true;
      }
    });
    return moduleClasses;
  }

  private static void goForIntermediateFiles(VirtualFile from, Set<VirtualFile> dirty, FactoryMap<VirtualFile, Set<VirtualFile>> deps, Set<VirtualFile> visited) {
    final Set<VirtualFile> set = deps.get(from);
    for (VirtualFile psiClass : set) {
      if (visited.add(psiClass)) {
        goForIntermediateFiles(psiClass, dirty, deps, visited);
      }
      if (dirty.contains(psiClass)) {
        dirty.add(from);
      }
    }
  }

  private Set<VirtualFile> calcCodeReferenceDependencies(VirtualFile vfile, final Set<VirtualFile> moduleFiles) {
    final PsiFile psi = PsiManager.getInstance(myProject).findFile(vfile);
    if (!(psi instanceof GroovyFile)) return Collections.emptySet();

    final Set<VirtualFile> deps = new THashSet<VirtualFile>();
    psi.acceptChildren(new PsiElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (element instanceof GrCodeReferenceElement) {
          GrCodeReferenceElement referenceElement = (GrCodeReferenceElement)element;
          try {
            final PsiElement target = referenceElement.resolve();
            if (target instanceof GrTypeDefinition || target instanceof GroovyScriptClass) {
              final VirtualFile targetFile = target.getContainingFile().getViewProvider().getVirtualFile();
              if (moduleFiles.contains(targetFile)) {
                deps.add(targetFile);
              }
            }
          }
          catch (Exception e) {
            LOG.error(e);
            //prevent our PSI errors from failing the entire compilation
          }
        }

        element.acceptChildren(this);
      }
    });
    return deps;
  }

  public boolean validateConfiguration(CompileScope compileScope) {
    VirtualFile[] files = compileScope.getFiles(GroovyFileType.GROOVY_FILE_TYPE, true);
    if (files.length == 0) return true;

    final Set<String> scriptExtensions = GroovyFileTypeLoader.getCustomGroovyScriptExtensions();

    Set<Module> modules = new HashSet<Module>();
    for (VirtualFile file : files) {
      if (scriptExtensions.contains(file.getExtension())) {
        continue;
      }

      ProjectRootManager rootManager = ProjectRootManager.getInstance(myProject);
      Module module = rootManager.getFileIndex().getModuleForFile(file);
      if (module != null) {
        modules.add(module);
      }
    }

    Set<Module> nojdkModules = new HashSet<Module>();
    for (Module module : modules) {
      if(!(module.getModuleType() instanceof JavaModuleType)) return true;
      final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
      if (sdk == null || !(sdk.getSdkType() instanceof JavaSdkType)) {
        nojdkModules.add(module);
        continue;
      }

      if (!LibrariesUtil.hasGroovySdk(module)) {
        if (!GroovyConfigUtils.getInstance().tryToSetUpGroovyFacetOntheFly(module)) {
          Messages.showErrorDialog(myProject, GroovyBundle.message("cannot.compile.groovy.files.no.facet", module.getName()),
                                   GroovyBundle.message("cannot.compile"));
          ModulesConfigurator.showDialog(module.getProject(), module.getName(), ClasspathEditor.NAME, false);
          return false;
        }
      }
    }

    if (!nojdkModules.isEmpty()) {
      final Module[] noJdkArray = nojdkModules.toArray(new Module[nojdkModules.size()]);
      if (noJdkArray.length == 1) {
        Messages.showErrorDialog(myProject, GroovyBundle.message("cannot.compile.groovy.files.no.sdk", noJdkArray[0].getName()),
                                 GroovyBundle.message("cannot.compile"));
      }
      else {
        StringBuffer modulesList = new StringBuffer();
        for (int i = 0; i < noJdkArray.length; i++) {
          if (i > 0) modulesList.append(", ");
          modulesList.append(noJdkArray[i].getName());
        }
        Messages.showErrorDialog(myProject, GroovyBundle.message("cannot.compile.groovy.files.no.sdk.mult", modulesList.toString()),
                                 GroovyBundle.message("cannot.compile"));
      }
      return false;
    }

    return true;
  }

}
