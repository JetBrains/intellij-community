// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.gant;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.AbstractConfigUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.util.GroovyUtils;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.util.ArrayList;

public final class GantUtils {
  @NonNls public static final String GANT_JAR_FILE_PATTERN = "gant((_groovy)?|-)\\d.*\\.jar";

  private GantUtils() {
  }

  public static GrArgumentLabel[] getScriptTargets(GroovyFile file) {
    ArrayList<GrArgumentLabel> labels = new ArrayList<>();
    for (PsiElement child : file.getChildren()) {
      if (child instanceof GrMethodCallExpression call) {
        GrNamedArgument[] arguments = call.getNamedArguments();
        if (arguments.length == 1) {
          GrArgumentLabel label = arguments[0].getLabel();
          if (label != null && isPlainIdentifier(label)) {
            labels.add(label);
          }
        }
      }
    }
    return labels.toArray(GrArgumentLabel.EMPTY_ARRAY);
  }

  public static boolean isPlainIdentifier(final GrArgumentLabel label) {
    final PsiElement elem = label.getNameElement();
    final ASTNode node = elem.getNode();
    if (node == null) return false;
    return node.getElementType() == GroovyTokenTypes.mIDENT;
  }

  public static @NlsSafe @Nullable String getGantVersionOrNull(String path) {
    return AbstractConfigUtils.getSDKJarVersion(path + "/lib", "gant-\\d.*\\.jar", AbstractConfigUtils.MANIFEST_PATH);
  }

  public static boolean isGantSdkHome(VirtualFile file) {
    if (file != null && file.isDirectory()) {
      final String path = file.getPath();
      if (GroovyUtils.getFilesInDirectoryByPattern(path + "/lib", GANT_JAR_FILE_PATTERN).length > 0) {
        return true;
      }
      if (GroovyUtils.getFilesInDirectoryByPattern(path + "/embeddable", GANT_JAR_FILE_PATTERN).length > 0) {
        return true;
      }
      if (file.findFileByRelativePath("bin/gant") != null) {
        return true;
      }
    }
    return false;
  }

  public static boolean isSDKLibrary(Library library) {
    if (library == null) return false;
    return isGantLibrary(library.getFiles(OrderRootType.CLASSES));
  }

  public static boolean isGantLibrary(VirtualFile[] files) {
    for (VirtualFile file : files) {
      if (isGantJarFile(file.getName())) {
        return true;
      }
    }
    return false;
  }

  public static boolean isGantJarFile(final String name) {
    return name.matches(GANT_JAR_FILE_PATTERN);
  }

  public static String getGantLibraryHome(Library library) {
    return getGantLibraryHome(library.getFiles(OrderRootType.CLASSES));
  }

  public static String getGantLibraryHome(VirtualFile[] files) {
    for (VirtualFile file : files) {
      if (isGantJarFile(file.getName())) {
        final VirtualFile parent = LibrariesUtil.getLocalFile(file).getParent();
        if (parent != null) {
          final VirtualFile gantHome = parent.getParent();
          if (gantHome != null) {
            return PathUtil.getLocalPath(gantHome);
          }
        }
      }
    }
    return "";
  }

  @NotNull
  public static String getSDKInstallPath(@Nullable Module module, @NotNull Project project) {
    if (module != null) {
      final String fromClasspath = getSdkHomeFromClasspath(module);
      if (fromClasspath != null) {
        return fromClasspath;
      }
    }

    final VirtualFile sdkHome = GantSettings.getInstance(project).getSdkHome();
    return sdkHome != null ? sdkHome.getPath() : "";
  }

  @Nullable
  public static String getSdkHomeFromClasspath(@NotNull Module module) {
    Library[] libraries = LibrariesUtil.getLibrariesByCondition(module, library1 -> isSDKLibrary(library1));
    if (libraries.length != 0) {
      final String home = getGantLibraryHome(libraries[0]);
      if (StringUtil.isNotEmpty(home)) {
        return home;
      }
    }
    return null;
  }

  public static boolean isSDKConfiguredToRun(@NotNull Module module) {
    return GantSettings.getInstance(module.getProject()).getSdkHome() != null || getSdkHomeFromClasspath(module) != null;
  }
}
