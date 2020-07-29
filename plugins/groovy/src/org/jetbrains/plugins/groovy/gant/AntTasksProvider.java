// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.gant;

import com.intellij.lang.ant.ReflectedProject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.ClassLoaderUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.runner.GroovyScriptUtil;

import java.net.URL;
import java.util.*;
import java.util.concurrent.Future;

import static org.jetbrains.plugins.groovy.gant.AntBuilderMethod.methods;

/**
 * @author ilyas, peter
 */
public final class AntTasksProvider {
  private static final Logger LOG = Logger.getInstance(AntTasksProvider.class);
  private static final Key<CachedValue<Set<LightMethodBuilder>>> GANT_METHODS = Key.create("gantMethods");
  private static final Object ourLock = new Object();
  public static final ParameterizedCachedValueProvider<Map<List<URL>,AntClassLoader>,Project> PROVIDER =
    project -> {
      final Map<List<URL>, AntClassLoader> map = ContainerUtil.createSoftValueMap();
      return CachedValueProvider.Result.create(map, ProjectRootManager.getInstance(project));
    };
  public static final Key<ParameterizedCachedValue<Map<List<URL>,AntClassLoader>,Project>> KEY =
    Key.create("ANtClassLoader");

  private AntTasksProvider() { }

  public static Set<LightMethodBuilder> getAntTasks(PsiElement place) {
    final PsiFile file = place.getContainingFile();
    if (!(file instanceof GroovyFile)) {
      return Collections.emptySet();
    }

    return CachedValuesManager.getManager(file.getProject()).getCachedValue(file, GANT_METHODS, () -> {
      Map<String, Class> antObjects = getAntObjects((GroovyFile)file);

      final Set<LightMethodBuilder> methods = new HashSet<>();

      final Project project = file.getProject();
      final PsiType mapType = TypesUtil.createType(GroovyCommonClassNames.JAVA_UTIL_LINKED_HASH_MAP, file);
      final PsiType stringType = TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, file);
      final PsiType closureType = TypesUtil.createType(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, file);

      for (String name : antObjects.keySet()) {
        methods.addAll(methods(file, name, antObjects.get(name), mapType, stringType, closureType));
      }
      return CachedValueProvider.Result
        .create(methods, PsiModificationTracker.MODIFICATION_COUNT, ProjectRootManager.getInstance(project));
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
      VirtualFile localFile = VfsUtil.getLocalFile(jar);
      if (localFile.isInLocalFileSystem()) {
        urls.add(VfsUtilCore.convertToURL(localFile.getUrl()));
      }
    }

    if (JavaPsiFacade.getInstance(project).findClass(ReflectedProject.ANT_PROJECT_CLASS, groovyFile.getResolveScope()) == null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Ant library not available in " + groovyFile.getVirtualFile().getPath() + "; urls=" + urls);
      }
      return Collections.emptyMap();
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

    AntClassLoader(ArrayList<URL> urls) {
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
    Map<String, Class> getAntObjects() {
      return ProgressIndicatorUtils.awaitWithCheckCanceled(myFuture);
    }
  }
}
