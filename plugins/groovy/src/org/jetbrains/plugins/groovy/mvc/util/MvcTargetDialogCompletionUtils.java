package org.jetbrains.plugins.groovy.mvc.util;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.mvc.MvcFramework;
import org.jetbrains.plugins.groovy.mvc.MvcModuleStructureSynchronizer;
import org.jetbrains.plugins.groovy.refactoring.GroovyNamesUtil;

import java.io.File;
import java.util.*;

/**
 * @author Sergey Evdokimov
 */
public class MvcTargetDialogCompletionUtils {
  
  private static final Key<CachedValue<Set<String>>> ALL_TARGET_KEY = Key.create("MvcTargetDialogCompletionUtils");

  private static final String[] SYSTEM_PROPERTIES = {
    "grails.home",

    // System properties from ivy
    "ivy.default.ivy.user.dir", "ivy.default.conf.dir",
    "ivy.local.default.root", "ivy.local.default.ivy.pattern", "ivy.local.default.artifact.pattern",
    "ivy.shared.default.root", "ivy.shared.default.ivy.pattern", "ivy.shared.default.artifact.pattern",
    "ivy.ivyrep.default.ivy.root", "ivy.ivyrep.default.ivy.pattern", "ivy.ivyrep.default.artifact.root", "ivy.ivyrep.default.artifact.pattern",


    // System properties from grails.util.BuildSettings
    "grails.servlet.version", "base.dir", "grails.work.dir", "grails.project.work.dir", "grails.project.war.exploded.dir",
    "grails.project.plugins.dir", "grails.global.plugins.dir", "grails.project.resource.dir", "grails.project.source.dir",
    "grails.project.web.xml", "grails.project.class.dir", "grails.project.plugin.class.dir", "grails.project.plugin.build.class.dir",
    "grails.project.plugin.provided.class.dir", "grails.project.test.class.dir", "grails.project.test.reports.dir",
    "grails.project.docs.output.dir", "grails.project.test.source.dir", "grails.project.target.dir", "grails.project.war.file",
    "grails.project.war.file", "grails.project.war.osgi.headers", "grails.build.listeners", "grails.project.compile.verbose",
    "grails.testing.functional.baseUrl", "grails.compile.artefacts.closures.convert"
  };

  private MvcTargetDialogCompletionUtils() {
  }

  public static Collection<LookupElement> collectVariants(@NotNull Module module, @NotNull String text, int offset, @NotNull String prefix) {
    List<LookupElement> res = new ArrayList<LookupElement>();

    if (prefix.startsWith("-D")) {
      for (String property : SYSTEM_PROPERTIES) {
        res.add(TailTypeDecorator.withTail(LookupElementBuilder.create("-D" + property), MyTailTypeEQ.INSTANCE));
      }

      return res;
    }

    if (text.substring(0, offset).matches("\\s*(?:(:?-\\S+|dev|prod|test)\\s+)*\\S*")) {
      for (String completionVariant : getAllTargetNames(module)) {
        res.add(TailTypeDecorator.withTail(LookupElementBuilder.create(completionVariant), TailType.SPACE));
      }
    }
    else {
      // Grails command already typed. Try to complete classes and packages names.
      //GlobalSearchScope.moduleScope(myModule);

      //PsiPackage defaultPackage = JavaPsiFacade.getInstance(myModule.getProject()).findPackage("");
    }

    return res;
  }

  public static Set<String> getAllTargetNamesInternal(@NotNull Module module) {
    final Set<String> result = new HashSet<String>();

    MvcFramework.addAvailableSystemScripts(result, module);

    MvcFramework framework = MvcModuleStructureSynchronizer.getFramework(module);
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
    CachedValue<Set<String>> cachedTargets = module.getUserData(ALL_TARGET_KEY);
    if (cachedTargets == null) {
      cachedTargets = CachedValuesManager.getManager(module.getProject()).createCachedValue(new CachedValueProvider<Set<String>>() {
          public Result<Set<String>> compute() {
            return Result.create(getAllTargetNamesInternal(module), PsiModificationTracker.MODIFICATION_COUNT);
          }
        }, false);

      cachedTargets = ((UserDataHolderEx)module).putUserDataIfAbsent(ALL_TARGET_KEY, cachedTargets);
    }

    return cachedTargets.getValue();
  }

  private static class MyTailTypeEQ extends TailType.TailTypeEQ {
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
