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
package org.jetbrains.plugins.groovy.gant;

import com.intellij.lang.ant.psi.impl.ReflectedProject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;

import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author ilyas, peter
 */
public class AntTasksProvider {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.gant.AntTasksProvider");
  public static final boolean antAvailable;
  private static final Key<CachedValue<Set<LightMethodBuilder>>> GANT_METHODS = Key.create("gantMethods");
  private static final Key<CachedValue<Map<String, Class>>> ANT_OBJECTS = Key.create("antObjects");

  private AntTasksProvider() {
  }

  static {
    boolean ant = false;
    try {
      Class.forName("com.intellij.lang.ant.psi.impl.ReflectedProject");
      ant = true;
    }
    catch (ClassNotFoundException ignored) {
    }
    antAvailable = ant;
  }

  public static Set<LightMethodBuilder> getAntTasks(PsiElement place) {
    final PsiFile file = place.getContainingFile();
    if (!(file instanceof GroovyFile)) {
      return Collections.emptySet();
    }

    return CachedValuesManager.getManager(file.getProject()).getCachedValue(file, GANT_METHODS, new CachedValueProvider<Set<LightMethodBuilder>>() {
      @Override
      public Result<Set<LightMethodBuilder>> compute() {
        Map<String, Class> antObjects = getAntObjects((GroovyFile)file);

        final Set<LightMethodBuilder> methods = new HashSet<LightMethodBuilder>();

        final Project project = file.getProject();
        final PsiType closureType = JavaPsiFacade.getElementFactory(project).createTypeFromText(GrClosableBlock.GROOVY_LANG_CLOSURE, file);
        final PsiClassType stringType = PsiType.getJavaLangString(file.getManager(), file.getResolveScope());

        for (String name : antObjects.keySet()) {
          methods.add(new AntBuilderMethod(file, name, closureType, antObjects.get(name), stringType));
        }
        final Result<Set<LightMethodBuilder>> result =
          Result.create(methods, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT, ProjectRootManager.getInstance(project));
        result.setLockValue(true);
        return result;
      }
    }, false);
  }

  private static Map<String, Class> getAntObjects(final GroovyFile groovyFile) {
    return CachedValuesManager
      .getManager(groovyFile.getProject()).getCachedValue(groovyFile, ANT_OBJECTS, new CachedValueProvider<Map<String, Class>>() {
        @Override
        public Result<Map<String, Class>> compute() {
          final Module module = ModuleUtil.findModuleForPsiElement(groovyFile);
          Set<VirtualFile> jars = new HashSet<VirtualFile>();
          if (module != null) {
            ContainerUtil.addAll(jars, OrderEnumerator.orderEntries(module).getAllLibrariesAndSdkClassesRoots());
          }

          if (groovyFile.isScript() && GroovyScriptTypeDetector.getScriptType(groovyFile) instanceof GantScriptType) {
            jars.addAll(GantScriptType.additionalScopeFiles(groovyFile));
          }

          final ArrayList<URL> urls = new ArrayList<URL>();
          for (VirtualFile jar : jars) {
            urls.add(VfsUtil.convertToURL(PathUtil.getLocalFile(jar).getUrl()));
          }
          final ClassLoader loader = new UrlClassLoader(urls, null, false, false, true);
          Future<ReflectedProject> future =  ApplicationManager.getApplication().executeOnPooledThread(new Callable<ReflectedProject>() {
            @Override
            public ReflectedProject call() throws Exception {
              try {
                return ReflectedProject.getProject(loader);
              }
              catch (Exception e) {
                LOG.error(e);
                return null;
              }
            }
          });

          ReflectedProject antProject = null;
          while (true) {
            try {
              antProject = future.get(100, TimeUnit.MILLISECONDS);
              break;
            }
            catch (TimeoutException ignore) {
            }
            catch (Exception e) {
              LOG.error(e);
              break;
            }
            ProgressManager.checkCanceled();
          }

          final Map<String, Class> result = new HashMap<String, Class>();
          if (antProject != null) {
            final Map<String, Class> taskDefinitions = antProject.getTaskDefinitions();
            if (taskDefinitions != null) {
              result.putAll(taskDefinitions);
            }
            final Map<String, Class> dataTypeDefinitions = antProject.getDataTypeDefinitions();
            if (dataTypeDefinitions != null) {
              result.putAll(dataTypeDefinitions);
            }
          }
          return Result.create(result, ProjectRootManager.getInstance(groovyFile.getProject()));
        }

      }, false);
  }

}
