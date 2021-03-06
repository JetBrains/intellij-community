// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.groovy;

import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Function;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.groovy.compiler.rt.GroovyRtConstants;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ExternalProcessUtil;
import org.jetbrains.jps.incremental.Utils;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.service.SharedThreadPool;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

/**
 * @author peter
 */
final class ForkedGroovyc implements GroovycFlavor {
  private final boolean myOptimizeClassLoading;
  private final ModuleChunk myChunk;

  ForkedGroovyc(boolean optimizeClassLoading, ModuleChunk chunk) {
    myOptimizeClassLoading = optimizeClassLoading;
    myChunk = chunk;
  }

  @Override
  public GroovycContinuation runGroovyc(Collection<String> compilationClassPath,
                                        boolean forStubs,
                                        CompileContext context,
                                        File tempFile,
                                        final GroovycOutputParser parser, String byteCodeTargetLevel)
    throws Exception {
    List<String> classpath = new ArrayList<>();
    if (myOptimizeClassLoading) {
      classpath.addAll(GroovyBuilder.getGroovyRtRoots());
      classpath.add(ClasspathBootstrap.getResourcePath(Function.class));
    }
    else {
      classpath.addAll(compilationClassPath);
    }

    JpsGroovySettings settings = JpsGroovycRunner.getGroovyCompilerSettings(context);

    List<String> vmParams = new ArrayList<>();
    vmParams.add("-Xmx" + System.getProperty("groovyc.heap.size", String.valueOf(Utils.suggestForkedCompilerHeapSize())) + "m");
    vmParams.add("-Dfile.encoding=" + System.getProperty("file.encoding"));
    //vmParams.add("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5239");

    if ("false".equals(System.getProperty(GroovyRtConstants.GROOVYC_ASM_RESOLVING_ONLY))) {
      vmParams.add("-D" + GroovyRtConstants.GROOVYC_ASM_RESOLVING_ONLY + "=false");
    }
    String configScript = settings.configScript;
    if (StringUtil.isNotEmpty(configScript)) {
      vmParams.add("-D" + GroovyRtConstants.GROOVYC_CONFIG_SCRIPT + "=" + configScript);
    }

    String grapeRoot = System.getProperty(GroovycOutputParser.GRAPE_ROOT);
    if (grapeRoot != null) {
      vmParams.add("-D" + GroovycOutputParser.GRAPE_ROOT + "=" + grapeRoot);
    }

    if (byteCodeTargetLevel != null) {
      vmParams.add("-D" + GroovyRtConstants.GROOVY_TARGET_BYTECODE + "=" + byteCodeTargetLevel);
    }
    if ("true".equals(System.getProperty(GroovyRtConstants.GROOVYC_LEGACY_REMOVE_ANNOTATIONS))) {
      vmParams.add("-D" + GroovyRtConstants.GROOVYC_LEGACY_REMOVE_ANNOTATIONS + "=true");
    }

    final List<String> cmd = ExternalProcessUtil.buildJavaCommandLine(
      getJavaExecutable(myChunk),
      "org.jetbrains.groovy.compiler.rt.GroovycRunner",
      Collections.emptyList(), classpath,
      vmParams,
      getProgramParams(tempFile, settings, forStubs)
    );
    final Process process = Runtime.getRuntime().exec(ArrayUtilRt.toStringArray(cmd));
    ProcessHandler handler = new BaseOSProcessHandler(process, StringUtil.join(cmd, " "), null) {
      @NotNull
      @Override
      public Future<?> executeTask(@NotNull Runnable task) {
        return SharedThreadPool.getInstance().submit(task);
      }

      @Override
      public void notifyTextAvailable(@NotNull String text, @NotNull Key outputType) {
        parser.notifyTextAvailable(text, outputType);
      }
    };

    handler.startNotify();
    handler.waitFor();
    parser.notifyFinished(process.exitValue());
    return null;
  }

  private List<String> getProgramParams(File tempFile, JpsGroovySettings settings, boolean forStubs) {
    List<String> programParams = ContainerUtil.newArrayList(myOptimizeClassLoading ? GroovyRtConstants.OPTIMIZE : "do_not_optimize",
                                                              forStubs ? "stubs" : "groovyc",
                                                            tempFile.getPath());
    if (settings.invokeDynamic) {
      programParams.add("--indy");
    }
    return programParams;
  }


  private static String getJavaExecutable(ModuleChunk chunk) {
    JpsSdk<?> sdk = GroovyBuilder.getJdk(chunk);
    return sdk != null ? JpsJavaSdkType.getJavaExecutable(sdk) : SystemProperties.getJavaHome() + "/bin/java";
  }

}
