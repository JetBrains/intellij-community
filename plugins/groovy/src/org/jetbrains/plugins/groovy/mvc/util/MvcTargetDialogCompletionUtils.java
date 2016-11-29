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
package org.jetbrains.plugins.groovy.mvc.util;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.mvc.MvcFramework;

import java.io.File;
import java.util.*;

/**
 * @author Sergey Evdokimov
 */
public class MvcTargetDialogCompletionUtils {

  private static final String[] SYSTEM_PROPERTIES = {
    // System properties from ivy
    "ivy.default.ivy.user.dir", "ivy.default.conf.dir",
    "ivy.local.default.root", "ivy.local.default.ivy.pattern", "ivy.local.default.artifact.pattern",
    "ivy.shared.default.root", "ivy.shared.default.ivy.pattern", "ivy.shared.default.artifact.pattern",
    "ivy.ivyrep.default.ivy.root", "ivy.ivyrep.default.ivy.pattern", "ivy.ivyrep.default.artifact.root",
    "ivy.ivyrep.default.artifact.pattern"
  };

  private static final NotNullLazyValue<List<LookupElement>> SYSTEM_PROPERTIES_VARIANTS = new NotNullLazyValue<List<LookupElement>>() {
    @NotNull
    @Override
    protected List<LookupElement> compute() {
      List<LookupElement> result = ContainerUtil.newArrayList();
      for (String property : SYSTEM_PROPERTIES) {
        result.add(TailTypeDecorator.withTail(LookupElementBuilder.create("-D" + property), MyTailTypeEQ.INSTANCE));
      }
      return Collections.unmodifiableList(result);
    }
  };
  
  private MvcTargetDialogCompletionUtils() {
  }

  public static List<LookupElement> getSystemPropertiesVariants() {
    return SYSTEM_PROPERTIES_VARIANTS.getValue();
  }
  
  public static Collection<LookupElement> collectVariants(@NotNull Module module, @NotNull String text, int offset, @NotNull String prefix) {
    if (prefix.startsWith("-D")) {
      return getSystemPropertiesVariants();
    }
    if (text.substring(0, offset).matches("\\s*(grails\\s*)?(?:(:?-D\\S+|dev|prod|test)\\s+)*\\S*")) {
      List<LookupElement> res = new ArrayList<>();
      // Complete command name because command name is not typed.
      for (String completionVariant : getAllTargetNames(module)) {
        res.add(TailTypeDecorator.withTail(LookupElementBuilder.create(completionVariant), TailType.SPACE));
      }
      return res;
    }
    else {
      // Command name already typed. Try to complete classes and packages names.
      GlobalSearchScope scope = GlobalSearchScope.moduleScope(module);
      return completeClassesAndPackages(prefix, scope);
    }
  }

  public static List<LookupElement> completeClassesAndPackages(@NotNull String prefix, @NotNull GlobalSearchScope scope) {
    if (scope.getProject() == null) return Collections.emptyList();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(scope.getProject());
    final List<LookupElement> res = new ArrayList<>();

    // Complete class names if prefix is a package name with dot at end.
    if (prefix.endsWith(".") && prefix.length() > 1) {
      PsiPackage p = facade.findPackage(prefix.substring(0, prefix.length() - 1));
      if (p != null) {
        for (PsiClass aClass : p.getClasses(scope)) {
          String qualifiedName = aClass.getQualifiedName();
          if (qualifiedName != null) {
            res.add(LookupElementBuilder.create(aClass, qualifiedName));
          }
        }
      }
    }

    PsiPackage defaultPackage = facade.findPackage("");
    if (defaultPackage != null) {
      collectClassesAndPackageNames(res, defaultPackage, scope);
    }

    return res;
  }

  private static void collectClassesAndPackageNames(Collection<LookupElement> res, @NotNull PsiPackage aPackage, GlobalSearchScope scope) {
    PsiPackage[] subPackages = aPackage.getSubPackages(scope);

    String qualifiedName = aPackage.getQualifiedName();
    if (!qualifiedName.isEmpty()) {
      if (subPackages.length == 0 || aPackage.getClasses(scope).length > 0) {
        res.add(TailTypeDecorator.withTail(LookupElementBuilder.create(qualifiedName), TailType.DOT));
      }
    }

    for (PsiPackage subPackage : subPackages) {
      collectClassesAndPackageNames(res, subPackage, scope);
    }
  }

  public static Set<String> getAllTargetNamesInternal(@NotNull Module module) {
    final Set<String> result = new HashSet<>();

    MvcFramework.addAvailableSystemScripts(result, module);

    MvcFramework framework = MvcFramework.getInstance(module);
    if (framework != null) {
      final VirtualFile root = framework.findAppRoot(module);
      if (root != null) {
        MvcFramework.addAvailableScripts(result, root);
      }

      for (VirtualFile pluginRoot : framework.getAllPluginRoots(module, false)) {
        MvcFramework.addAvailableScripts(result, pluginRoot);
      }
    }

    collectScriptsFromUserHome(result);

    return result;
  }

  private static void collectScriptsFromUserHome(Set<String> result) {
    String userHome = SystemProperties.getUserHome();
    if (userHome == null) return;

    File scriptFolder = new File(userHome, ".grails/scripts");

    File[] files = scriptFolder.listFiles();

    if (files == null) return;

    for (File file : files) {
      if (file.getName().startsWith("IdeaPrintProjectSettings")) continue;

      if (isScriptFile(file)) {
        String name = file.getName();
        int idx = name.lastIndexOf('.');
        if (idx != -1) {
          name = name.substring(0, idx);
        }

        result.add(GroovyNamesUtil.camelToSnake(name));
      }
    }
  }

  public static boolean isScriptFile(File file) {
    return file.isFile() && MvcFramework.isScriptFileName(file.getName());
  }

  public static Set<String> getAllTargetNames(@NotNull final Module module) {
    return CachedValuesManager.getManager(module.getProject()).getCachedValue(
      module,
      () -> CachedValueProvider.Result.create(getAllTargetNamesInternal(module), PsiModificationTracker.MODIFICATION_COUNT)
    );
  }

  public static class MyTailTypeEQ extends TailType.TailTypeEQ {
    public static final MyTailTypeEQ INSTANCE = new MyTailTypeEQ();

    @Override
    protected boolean isSpaceAroundAssignmentOperators(Editor editor, int tailOffset) {
      return false;
    }

    public String toString() {
      return "MvcTargetDialogCompletionUtils.TailTypeEQ";
    }
  }

}
