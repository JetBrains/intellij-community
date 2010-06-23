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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.PathUtil;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;

import java.net.URL;
import java.util.*;

/**
 * @author ilyas
 */
public class AntTasksProvider {
  public static final boolean antAvailable;
  private static final Key<CachedValue<Set<LightMethodBuilder>>> ANT_OBJECTS = Key.create("antObjects");
  private static final Key<CachedValue<ClassLoader>> CLASS_LOADER = Key.create("gantClassLoader");

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

    return CachedValuesManager.getManager(file.getProject()).getCachedValue(file, ANT_OBJECTS, new CachedValueProvider<Set<LightMethodBuilder>>() {
      @Override
      public Result<Set<LightMethodBuilder>> compute() {
        return Result.create(findAntTasks((GroovyFile)file), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT, ProjectRootManager.getInstance(file.getProject()));
      }
    }, false);
  }

  private static Set<LightMethodBuilder> findAntTasks(final GroovyFile groovyFile) {
    ClassLoader classLoader = getAntClassLoader(groovyFile);

    final Set<LightMethodBuilder> result = new HashSet<LightMethodBuilder>();
    final ReflectedProject antProject = ReflectedProject.getProject(classLoader);

    mapAntObjectsToGant(groovyFile, antProject.getDataTypeDefinitions(), result);
    mapAntObjectsToGant(groovyFile, antProject.getTaskDefinitions(), result);
    return result;
  }

  private static ClassLoader getAntClassLoader(final GroovyFile groovyFile) {
    return CachedValuesManager
      .getManager(groovyFile.getProject()).getCachedValue(groovyFile, CLASS_LOADER, new CachedValueProvider<ClassLoader>() {
        @Override
        public Result<ClassLoader> compute() {
          final Module module = ModuleUtil.findModuleForPsiElement(groovyFile);
          Set<VirtualFile> jars = new HashSet<VirtualFile>();
          if (module != null) {
            jars.addAll(Arrays.asList(ModuleRootManager.getInstance(module).getFiles(OrderRootType.CLASSES)));
          }

          if (groovyFile.isScript() && GroovyScriptType.getScriptType(groovyFile) instanceof GantScriptType) {
            jars.addAll(GantScriptType.additionalScopeFiles(groovyFile));
          }

          final ArrayList<URL> urls = new ArrayList<URL>();
          for (VirtualFile jar : jars) {
            urls.add(VfsUtil.convertToURL(PathUtil.getLocalFile(jar).getUrl()));
          }
          final ClassLoader loader = new UrlClassLoader(urls, null);
          return Result.create(loader, ProjectRootManager.getInstance(groovyFile.getProject()));
        }

      }, false);
  }

  private static void mapAntObjectsToGant(PsiElement place, Map<String, Class> antObjects, Set<LightMethodBuilder> result) {
    final Project project = place.getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final GlobalSearchScope scope = place.getResolveScope();

    final PsiType closureType = JavaPsiFacade.getElementFactory(project).createTypeFromText(GrClosableBlock.GROOVY_LANG_CLOSURE, place);
    
    for (String name : antObjects.keySet()) {
      final PsiClass psiClass = facade.findClass(antObjects.get(name).getName(), scope);

      final LightMethodBuilder tdMethod =
        new LightMethodBuilder(PsiManager.getInstance(project), GroovyFileType.GROOVY_LANGUAGE, name).
          setModifiers(PsiModifier.PUBLIC).
          addParameter("args", CommonClassNames.JAVA_UTIL_MAP).
          setBaseIcon(GantIcons.ANT_TASK);

      tdMethod.addParameter(new GrLightParameter("body", closureType, tdMethod).setOptional(true));

      if (psiClass != null) {
        tdMethod.setNavigationElement(psiClass);
      }
      result.add(tdMethod);
    }
  }

}
