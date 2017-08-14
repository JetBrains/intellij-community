/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.lang.ant.ReflectedProject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.ClassLoaderUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.util.*;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.runner.GroovyScriptUtil;

import java.net.URL;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author ilyas, peter
 */
public class AntTasksProvider {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.gant.AntTasksProvider");
  private static final Key<CachedValue<Set<LightMethodBuilder>>> GANT_METHODS = Key.create("gantMethods");
  private static final Object ourLock = new Object();
  public static final ParameterizedCachedValueProvider<Map<List<URL>,AntClassLoader>,Project> PROVIDER =
    new ParameterizedCachedValueProvider<Map<List<URL>, AntClassLoader>, Project>() {
      @Nullable
      @Override
      public CachedValueProvider.Result<Map<List<URL>, AntClassLoader>> compute(Project project) {
        final Map<List<URL>, AntClassLoader> map = ContainerUtil.createSoftValueMap();
        return CachedValueProvider.Result.create(map, ProjectRootManager.getInstance(project));
      }
    };
  public static final Key<ParameterizedCachedValue<Map<List<URL>,AntClassLoader>,Project>> KEY =
    Key.create("ANtClassLoader");

  private AntTasksProvider() {
  }

  public static Set<LightMethodBuilder> getAntTasks(PsiElement place) {
    final PsiFile file = place.getContainingFile();
    if (!(file instanceof GroovyFile)) {
      return Collections.emptySet();
    }

    return CachedValuesManager.getManager(file.getProject()).getCachedValue(file, GANT_METHODS, () -> {
      Map<String, Class> antObjects = getAntObjects((GroovyFile)file);

      final Set<LightMethodBuilder> methods = new HashSet<>();

      final Project project = file.getProject();
      final PsiType closureType = TypesUtil.createType(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, file);
      final PsiClassType stringType = TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, file);

      for (String name : antObjects.keySet()) {
        methods.add(new AntBuilderMethod(file, name, closureType, antObjects.get(name), stringType));
      }
      return CachedValueProvider.Result
        .create(methods, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT, ProjectRootManager.getInstance(project));
    }, false);
  }

  private static Map<String, Class> getAntObjects(final GroovyFile groovyFile) {
    final Project project = groovyFile.getProject();

    final Module module = ModuleUtilCore.findModuleForPsiElement(groovyFile);
    Set<VirtualFile> jars = new HashSet<>();
    if (module != null) {
      ContainerUtil.addAll(jars, OrderEnumerator.orderEntries(module).getAllLibrariesAndSdkClassesRoots());
    }

    if (groovyFile.isScript() && GroovyScriptUtil.getScriptType(groovyFile) instanceof GantScriptType) {
      jars.addAll(GantScriptType.additionalScopeFiles(groovyFile));
    }

    final ArrayList<URL> urls = new ArrayList<>();
    for (VirtualFile jar : jars) {
      VirtualFile localFile = PathUtil.getLocalFile(jar);
      if (localFile.getFileSystem() instanceof LocalFileSystem) {
        urls.add(VfsUtilCore.convertToURL(localFile.getUrl()));
      }
    }

    AntClassLoader loader;
    synchronized (ourLock) {
      final Map<List<URL>, AntClassLoader> map = CachedValuesManager.getManager(project).getParameterizedCachedValue(project, KEY,
                                                                                                                     PROVIDER, false, project);

      loader = map.get(urls);
      if (loader == null) {
        map.put(urls, loader = new AntClassLoader(urls));
      }
    }

    return loader.getAntObjects();
  }

  private static class AntClassLoader extends UrlClassLoader {
    private final Future<Map<String, Class>> myFuture;

    public AntClassLoader(ArrayList<URL> urls) {
      super(getBuilder(urls));
      myFuture = ApplicationManager.getApplication().executeOnPooledThread(() -> {
        try {
          final ReflectedProject antProject = ReflectedProject.getProject(this);
          final Map<String, Class> result = new HashMap<>();
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
          return result;
        }
        catch (Exception e) {
          LOG.error(e);
          return null;
        }
      });
    }

    private static Builder getBuilder(ArrayList<URL> urls) {
      Builder builder = build()
        .urls(urls)
        .allowUnescaped()
        .noPreload();
      ClassLoaderUtil.addPlatformLoaderParentIfOnJdk9(builder);
      return builder;
    }

    @NotNull
    public Map<String, Class> getAntObjects() {
      while (true) {
        try {
          final Map<String, Class> map = myFuture.get(100, TimeUnit.MILLISECONDS);
          if (map != null) {
            return map;
          }
        }
        catch (TimeoutException ignore) {
        }
        catch (Exception e) {
          LOG.error(e);
          break;
        }
        ProgressManager.checkCanceled();
      }
      return Collections.emptyMap();
    }
  }
}
